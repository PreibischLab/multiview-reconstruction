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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.n5.WriteSequenceToN5;
import bdv.img.n5.N5ImageLoader;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import util.URITools;

public class Resave_N5 implements PlugIn
{
	@Override
	public void run(String arg)
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "Resaving as N5", "Resave", true, true, true, true, true ) )
			return;

		final ParametersResaveN5 n5params = ParametersResaveN5.getParamtersIJ( xml.getXMLURI(), xml.getViewSetupsToProcess(), true );

		if ( n5params == null )
			return;

		final ArrayList< ViewId > vidsToProcess = new ArrayList<>();
		for ( final TimePoint tp : xml.getTimePointsToProcess() )
			for ( final ViewSetup vs : xml.getViewSetupsToProcess() )
				vidsToProcess.add( new ViewId( tp.getId(), vs.getId() ) );

		resaveN5( xml.getData(), vidsToProcess, n5params );
	}


	public static void resaveN5(
			final SpimData2 data,
			final Collection<? extends ViewId> vidsToResave,
			final ParametersResaveN5 n5Params )
	{
		final SpimData2 sdReduced = Resave_HDF5.reduceSpimData2( data, vidsToResave.stream().collect( Collectors.toList() ) );

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( new Date( System.currentTimeMillis() ) + ": Saving " + n5Params.n5URI );

		//final Map< Integer, ExportMipmapInfo > proposedMipmaps;
		if ( n5Params.proposedMipmaps == null )
		{
			// propose downsampling
			n5Params.proposedMipmaps = Resave_HDF5.proposeMipmaps( sdReduced.getSequenceDescription().getViewSetupsOrdered() );

			// crude overwrite of block size (should be bigger than for normal hdf5)
			n5Params.proposedMipmaps.keySet().forEach( k -> {
				ExportMipmapInfo exportMipmapInfo = n5Params.proposedMipmaps.get( k );
				for (int[] row : exportMipmapInfo.getSubdivisions())
				{
					Arrays.fill( row, ParametersResaveN5.defaultBlockSize );
					row[ 0 ] = ParametersResaveN5.defaultBlockSizeXY;
					if ( row.length >= 2 )
						row[ 1 ] = ParametersResaveN5.defaultBlockSizeXY;
				}
			});
		}

		// re-save data to file
		if ( URITools.isFile( n5Params.n5URI ) )
		{
			try
			{
				WriteSequenceToN5.writeN5File(
						sdReduced.getSequenceDescription(),
						n5Params.proposedMipmaps,
						n5Params.compression, //new GzipCompression()
						new File( URITools.removeFilePrefix( n5Params.n5URI ) ),
						new bdv.export.ExportScalePyramid.DefaultLoopbackHeuristic(),
						null,
						n5Params.numCellCreatorThreads, // Runtime.getRuntime().availableProcessors()
						progressWriter );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if ( URITools.isS3( n5Params.n5URI ) || URITools.isGC( n5Params.n5URI ) )
		{
			// TODO: save to cloud
		}

		sdReduced.getSequenceDescription().setImgLoader( new N5ImageLoader( n5Params.n5URI, sdReduced.getSequenceDescription() ) );
		sdReduced.setBasePathURI( n5Params.xmlURI );

		progressWriter.out().println( new Date( System.currentTimeMillis() ) + ": Saving " + n5Params.xmlURI );

		new XmlIoSpimData2().save( sdReduced, n5Params.xmlURI );

		progressWriter.setProgress( 1.0 );
		progressWriter.out().println( new Date( System.currentTimeMillis() ) + ": Finished saving " + n5Params.n5URI + " and " + n5Params.xmlURI );
	}


	public static void main(String[] args)
	{
		new ImageJ();
		new Resave_N5().run( null );
	}
}
