/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.Bzip2Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.Lz4Compression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.XzCompression;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.n5.WriteSequenceToN5;
import bdv.img.n5.BdvN5Format;
import bdv.img.n5.N5ImageLoader;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.headless.resave.HeadlessParseQueryXML;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class Resave_N5 implements PlugIn
{
	@Override
	public void run(String arg)
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "Resaving as N5", "Resave", true, true, true, true, true ) )
			return;

		final N5Parameters n5params = N5Parameters.getParamtersIJ( xml.getXMLFileName(), xml.getViewSetupsToProcess(), false );

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
			final N5Parameters n5Params )
	{
		final SpimData2 sdReduced = Resave_HDF5.reduceSpimData2( data, vidsToResave.stream().collect( Collectors.toList() ) );

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( new Date( System.currentTimeMillis() ) + ": Saving " + n5Params.n5File.getAbsolutePath() );

		// re-save data always if we have no cluster parameters, otherwise only if we asked for resave
		if ( n5Params.saveData )
		{
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
						Arrays.fill( row, N5Parameters.defaultBlockSize );
						row[ 0 ] = N5Parameters.defaultBlockSizeXY;
						if ( row.length >= 2 )
							row[ 1 ] = N5Parameters.defaultBlockSizeXY;
					}
				});
			}

			final Map< Integer, ExportMipmapInfo > proposedMipmaps = n5Params.proposedMipmaps;

			try
			{
				WriteSequenceToN5.writeN5File(
						sdReduced.getSequenceDescription(),
						proposedMipmaps,
						n5Params.compression, //new GzipCompression()
						n5Params.n5File,
						new bdv.export.ExportScalePyramid.DefaultLoopbackHeuristic(),
						null,
						n5Params.numCellCreatorThreads, // Runtime.getRuntime().availableProcessors()
						progressWriter );

				if ( n5Params.setFinishedAttributeInN5 )
				{
					final N5FSWriter n5 = new N5FSWriter( n5Params.n5File.getAbsolutePath() );

					for (ViewId vid : vidsToResave)
						n5.setAttribute( BdvN5Format.getPathName( vid.getViewSetupId(), vid.getTimePointId() ), N5Parameters.finishedAttrib, true);
				}
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}

		sdReduced.getSequenceDescription().setImgLoader( new N5ImageLoader( n5Params.n5File, sdReduced.getSequenceDescription() ) );
		sdReduced.setBasePath( n5Params.xmlFile.getParentFile() );

		if ( n5Params.saveXML )
		{
			try
			{
				new XmlIoSpimData2("").save( sdReduced, n5Params.xmlFile.getAbsolutePath() );
			}
			catch ( SpimDataException e )
			{
				e.printStackTrace();
			}
		}

		progressWriter.setProgress( 1.0 );
		progressWriter.out().println( new Date( System.currentTimeMillis() ) + ": Finished saving " + n5Params.n5File.getAbsolutePath() );
	}

	private static class Arguments implements Serializable
	{
		private static final long serialVersionUID = -1467734459169624759L;

		@Option(name = "-i", aliases = { "--inputXMLPath" }, required = true,
				usage = "Path to an input SpimData XML")
		private String inputXMLPath;

		@Option(name = "-x", aliases = { "--outputXMLPath (default: input-n5.xml)" }, required = false,
				usage = "Path to an output SpimData XML")
		private String outputXMLPath = null;
		
		@Option(name = "-o", aliases = { "--outputContainerPath" }, required = false,
				usage = "Path to an N5 container to store the output datasets  (default: input.n5 with xml dropped)")
		private String outputContainerPath = null;

		@Option(name = "-c", aliases = { "--compression" }, required = false,
				usage = "Compression options bzip2, gzip, lz4, raw, xz (default: gzip)")
		private String compression = "gzip";

		@Option(name = "-t", aliases = { "--numThreads" }, required = false,
				usage = "Number of threads used for saving, must be >= 1 (default: 1)")
		private int numThreads = 1;

		@Option(name = "-b", aliases = { "--blocksizes" }, required = false,
				usage = "Which block sizes to use, e.g. { { {64,64,64}, {64,64,64}, {64,64,64}, {64,64,64} }... (default: proposed by BigDataViewer)")
		private String blocksize = null;

		@Option(name = "-s", aliases = { "--subsampling" }, required = false,
				usage = "Which subsampling to use, e.g. { {1,1,1}, {2,2,2}, {4,4,4}, {8,8,8} }... (default: proposed by BigDataViewer)")
		private String subsampling = null;

		@Option(name = "-v", aliases = { "--viewsToResave" }, required = false,
				usage = "Which views to resave (timepoint,viewid),(,)... (default: all views)")
		private String viewsToResave = null;

		@Option(name = "-nd", aliases = { "--noResaveData" }, required = false,
				usage = "skips actual saving of data, so just XML is generated (default: false)")
		private boolean noResaveData = false;

		@Option(name = "-nx", aliases = { "--noResaveXML" }, required = false,
				usage = "skips actual saving of XML (default: false)")
		private boolean noResaveXML = false;

		@Option(name = "-nfa", aliases = { "--noFinishAttribute" }, required = false,
				usage = "skips setting a finish attribute after writing each View to the N5 (default: false)")
		private boolean noFinishAttribute = false;

		public Arguments( final String... args ) throws IllegalArgumentException
		{
			final CmdLineParser parser = new CmdLineParser( this );
			try
			{
				parser.parseArgument( args );
			}
			catch ( final CmdLineException e )
			{
				System.err.println( e.getMessage() );
				parser.printUsage( System.err );
				System.exit( 1 );
			}
		}

		public N5Parameters getParameters( final List< ViewId > viewIdsToResave )
		{
			final N5Parameters n5params = new N5Parameters();

			// make default paths from XML path if not provided
			final String outXML = this.getOutputXMLPath() != null ? this.getOutputXMLPath() : this.getInputXMLPath().replace( ".xml", "-n5.xml" );
			final String outN5 = this.getOutputContainerPath() != null ? this.getOutputContainerPath() : this.getInputXMLPath().replace( ".xml", ".n5" );

			n5params.n5File = new File(outN5);
			n5params.xmlFile = new File(outXML);

			n5params.saveData = !this.isNoResaveData();
			n5params.saveXML = !this.isNoResaveXML();

			if ( this.getCompression().toLowerCase().trim().equals( "bzip2" ) ) // "Bzip2", "Gzip", "Lz4", "Raw (no compression)", "Xz"
				n5params.compression = new Bzip2Compression();
			else if ( this.getCompression().toLowerCase().trim().equals( "gzip" ) )
				n5params.compression = new GzipCompression();
			else if ( this.getCompression().toLowerCase().trim().equals( "lz4" ) )
				n5params.compression = new Lz4Compression();
			else if ( this.getCompression().toLowerCase().trim().equals( "xz" ) )
				n5params.compression = new XzCompression();
			else if ( this.getCompression().toLowerCase().trim().equals( "raw" ) )
				n5params.compression = new RawCompression();
			else
			{
				IOFunctions.println( "Cannot parse compression argument: " + this.getCompression() );
				return null;
			}

			n5params.setFinishedAttributeInN5 = !this.isNoFinishAttrib();
			n5params.numCellCreatorThreads = Math.max( 1, this.getNumThreads() );

			if ( this.getSubsampling() == null && this.getBlocksize() == null )
			{
				n5params.proposedMipmaps = null;
			}
			else if ( this.getSubsampling() == null || this.getBlocksize() == null )
			{
				IOFunctions.println( "You must define either subsampling AND blocksize or nothing of both." );
				IOFunctions.println( "Subsampling: " +  this.getSubsampling() );
				IOFunctions.println( "Blocksize: " +  this.getBlocksize() );

				return null;
			}
			else
			{
				final int[][] resolutions = PluginHelper.parseResolutionsString( this.getSubsampling() );
				final int[][] subdivisions = PluginHelper.parseResolutionsString( this.getBlocksize() );
		
				if ( resolutions.length == 0 )
				{
					IOFunctions.println( "Cannot parse subsampling factors " + this.getSubsampling() );
					return null;
				}
				if ( subdivisions.length == 0 )
				{
					IOFunctions.println( "Cannot parse hdf5 chunk sizes " + this.getBlocksize() );
					return null;
				}
				else if ( resolutions.length != subdivisions.length )
				{
					IOFunctions.println( "subsampling factors and hdf5 chunk sizes must have the same number of elements" );
					return null;
				}

				//final Hash
				n5params.proposedMipmaps = N5Parameters.createProposedMipMaps( resolutions, subdivisions, viewIdsToResave.stream().map( vid -> vid.getViewSetupId() ).collect( Collectors.toSet() ) );
			}

			return n5params;
		}

		public List< ViewId > getViewIdsToResave( final Set< ? extends ViewId > allViewDescriptions )
		{
			// parse views to resave, or use all if not provided
			final List< ViewId > vidsToResave = getViewsToResave() != null ?
						parseViewIdString( getViewsToResave() ) : new ArrayList<>( allViewDescriptions );

			for ( final ViewId v : vidsToResave )
			{
				if ( !allViewDescriptions.contains( v ) )
				{
					System.out.println( "Cannot resave ViewId " + Group.pvid( v ) + ", it is not part of the XML." );
					System.err.println( "Cannot resave ViewId " + Group.pvid( v ) + ", it is not part of the XML." );

					return null;
				}
			}

			return vidsToResave;
		}

		public static List<ViewId> parseViewIdString(String input)
		{
			final ArrayList<ViewId> result = new ArrayList<>();
			for (String vid : input.split( Pattern.quote( ")," )))
			{
				String[] vidPair = vid.replace( ")", "" ).replace( "(", "" ).split( "," );
				result.add( new ViewId( Integer.parseInt( vidPair[0] ), Integer.parseInt( vidPair[1] ) ) );
			}
			return result;
		}

		public String getInputXMLPath()
		{
			return inputXMLPath;
		}

		public String getOutputXMLPath()
		{
			return outputXMLPath;
		}

		public String getOutputContainerPath()
		{
			return outputContainerPath;
		}

		public String getBlocksize()
		{
			return blocksize;
		}

		public String getSubsampling()
		{
			return subsampling;
		}
		
		public String getCompression()
		{
			return compression;
		}

		public int getNumThreads()
		{
			return numThreads;
		}

		public boolean isNoResaveData()
		{
			return noResaveData;
		}

		public boolean isNoResaveXML()
		{
			return noResaveXML;
		}

		public String getViewsToResave()
		{
			return viewsToResave;
		}

		public boolean isNoFinishAttrib()
		{
			return noFinishAttribute;
		}
	}

	public static void main(String[] args)
	{
		/*
		new ImageJ();
		new Resave_N5().run( null );
		SimpleMultiThreading.threadHaltUnClean();
		*/

		final Arguments parsedArgs = new Arguments( args );

		// try to load SpimData
		final HeadlessParseQueryXML xml = new HeadlessParseQueryXML();
		if ( !xml.loadXML( parsedArgs.getInputXMLPath(), false ) )
		{
			IOFunctions.println( "Could not load xml: " + parsedArgs.getInputXMLPath() );
			return;
		}

		final List< ViewId > viewIdsToResave = parsedArgs.getViewIdsToResave( xml.getData().getSequenceDescription().getViewDescriptions().keySet() );

		if ( viewIdsToResave == null )
			return;

		final N5Parameters n5Params = parsedArgs.getParameters( viewIdsToResave );

		if ( n5Params == null )
			return;

		resaveN5( xml.getData(), viewIdsToResave, n5Params );
	}
}
