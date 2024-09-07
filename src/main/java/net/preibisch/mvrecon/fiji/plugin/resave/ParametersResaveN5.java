/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2024 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.fiji.plugin.resave;

import java.awt.Font;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.Bzip2Compression;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.Lz4Compression;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.XzCompression;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.sequence.ViewSetup;
import net.preibisch.legacy.io.IOFunctions;

public class ParametersResaveN5
{
	public static String[] compressions = new String[]{ "Bzip2", "Gzip", "Lz4", "Raw (no compression)", "Xz" };
	public static int defaultBlockSize = 64;
	public static int defaultBlockSizeXY = 128;
	public static int defaultCompression = 1;
	public static int defaultNumThreads = Math.max( 1, Runtime.getRuntime().availableProcessors() - 1 );

	public URI xmlURI, n5URI;

	public int[][] resolutions, subdivisions;
	public Map< Integer, ExportMipmapInfo > proposedMipmaps;

	public Compression compression;
	public int numCellCreatorThreads = 1;

	//public boolean saveXML = true; // mostly important for cluster-based re-saving
	//public boolean saveData = true; // mostly important for cluster-based re-saving

	//public boolean setFinishedAttributeInN5 = true; // required if double-checking that all ViewId were written
	//final public static String finishedAttrib = "saved_completely"; // required if double-checking that all ViewId were written

	public static ParametersResaveN5 getParamtersIJ(
			final Collection< ViewSetup > setupsToProcess )
	{
		return getParamtersIJ( null, null, setupsToProcess, false );
	}

	public static ParametersResaveN5 getParamtersIJ(
			final URI xmlURI,
			final Collection< ViewSetup > setupsToProcess,
			final boolean askForPaths )
	{
		final URI n5URI = createN5URIfromXMLURI( xmlURI );

		return getParamtersIJ( xmlURI, n5URI, setupsToProcess, askForPaths );
	}

	public static URI createN5URIfromXMLURI( final URI xmlURI )
	{
		return URI.create( xmlURI.toString().subSequence( 0, xmlURI.toString().length() - 4 ) + ".n5" );
	}

	public static ParametersResaveN5 getParamtersIJ(
			final URI xmlURI,
			final URI n5URI,
			final Collection< ViewSetup > setupsToProcess,
			final boolean askForPaths )
	{
		final ParametersResaveN5 n5params = new ParametersResaveN5();

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( setupsToProcess ); //xml.getViewSetupsToProcess() );
		final int firstviewSetupId = setupsToProcess.iterator().next().getId();// xml.getData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final ExportMipmapInfo autoMipmapSettings = perSetupExportMipmapInfo.get( firstviewSetupId );

		// block size should be bigger than hdf5, and all the same
		for ( final int[] row : autoMipmapSettings.getSubdivisions() )
		{
			Arrays.fill( row, defaultBlockSize );
			row[ 0 ] = ParametersResaveN5.defaultBlockSizeXY;
			if ( row.length >= 2 )
				row[ 1 ] = ParametersResaveN5.defaultBlockSizeXY;
		}

		final GenericDialogPlus gdp = new GenericDialogPlus( "Options" );

		gdp.addMessage( "N5 saving options", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		gdp.addChoice( "Compression", compressions, compressions[ defaultCompression ] );
		gdp.addStringField( "Downsampling_factors", ProposeMipmaps.getArrayString( autoMipmapSettings.getExportResolutions() ), 40 );
		gdp.addStringField( "Block_size (all the same)", ProposeMipmaps.getArrayString( autoMipmapSettings.getSubdivisions() ), 40 );
		gdp.addNumericField( "Number_of_threads (CPUs:" + Runtime.getRuntime().availableProcessors() + ")", defaultNumThreads, 0 );

		if ( askForPaths )
		{
			gdp.addMessage( "" );
			gdp.addDirectoryField( "XML_path", xmlURI.toString(), 65 );
			gdp.addDirectoryField( "N5_path", n5URI.toString(), 65 );
		}

		gdp.showDialog();

		if (gdp.wasCanceled())
			return null;

		final int compression = defaultCompression = gdp.getNextChoiceIndex();

		final String subsampling = gdp.getNextString();
		final String chunkSizes = gdp.getNextString();

		n5params.numCellCreatorThreads = defaultNumThreads = Math.max( 1, (int)Math.round( gdp.getNextNumber() ) );

		if ( askForPaths )
		{
			try
			{
				n5params.xmlURI = new URI( gdp.getNextString() );
				n5params.n5URI = new URI( gdp.getNextString() );
			}
			catch ( URISyntaxException e )
			{
				IOFunctions.println( "Cannot create URIs for provided paths: " + e );
				return null;
			}

			IOFunctions.println( "XML & metadata path: " + n5params.xmlURI );
			IOFunctions.println( "Image data path: " + n5params.n5URI );
		}
		else
		{
			n5params.xmlURI = xmlURI;
			n5params.n5URI = n5URI;
		}

		if ( compression == 0 ) // "Bzip2", "Gzip", "Lz4", "Raw (no compression)", "Xz"
			n5params.compression = new Bzip2Compression();
		else if ( compression == 1 )
			n5params.compression = new GzipCompression();
		else if ( compression == 2 )
			n5params.compression = new Lz4Compression();
		else if ( compression == 4 )
			n5params.compression = new XzCompression();
		else
			n5params.compression = new RawCompression();

		n5params.resolutions = PluginHelper.parseResolutionsString( subsampling );
		n5params.subdivisions = PluginHelper.parseResolutionsString( chunkSizes );

		if ( n5params.resolutions.length == 0 )
		{
			IOFunctions.println( "Cannot parse downsampling factors " + subsampling );
			return null;
		}

		if ( n5params.subdivisions.length == 0 )
		{
			IOFunctions.println( "Cannot parse block sizes " + chunkSizes );
			return null;
		}
		else if ( n5params.resolutions.length != n5params.subdivisions.length )
		{
			IOFunctions.println( "downsampling factors and block sizes must have the same number of elements" );
			return null;
		}

		n5params.proposedMipmaps = createProposedMipMaps(
				n5params.resolutions,
				n5params.subdivisions,
				setupsToProcess.stream().map( vs -> vs.getId() ).collect( Collectors.toList() ) );

		return n5params;
	}

	public static Map< Integer, ExportMipmapInfo > createProposedMipMaps(
			final int[][] resolutions,
			final int[][] subdivisions,
			final Collection< Integer > setupIds )
	{
		final Map< Integer, ExportMipmapInfo > proposedMipmaps = new HashMap<>();

		final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( resolutions, subdivisions );
		for ( final int setupId : setupIds )
			proposedMipmaps.put( setupId, mipmapInfo );

		return proposedMipmaps;
	}
}
