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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.N5Writer;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.img.n5.N5ImageLoader;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.process.resave.N5ResaveTools;
import net.preibisch.mvrecon.process.resave.SpimData2Tools;
import util.Grid;
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

		resaveN5( xml.getData(), vidsToProcess, n5params, true );
	}


	public static SpimData2 resaveN5(
			final SpimData2 data,
			final Collection<? extends ViewId> vidsToResave,
			final ParametersResaveN5 n5Params,
			final boolean saveXML )
	{
		final SpimData2 sdReduced = SpimData2Tools.reduceSpimData2( data, vidsToResave.stream().collect( Collectors.toList() ) );

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

		/*
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
		else if ( URITools.isS3( n5Params.n5URI ) || URITools.isGC( n5Params.n5URI ) )*/
		{
			// save to cloud or file
			final N5Writer n5Writer = URITools.instantiateGuessedN5Writer( n5Params.n5URI );

			final int[] blockSize = n5Params.subdivisions[ 0 ];
			final int[] computeBlockSize = n5Params.subdivisions[ 0 ];
			final Compression compression = n5Params.compression;

			computeBlockSize[ 0 ] *= 4;
			computeBlockSize[ 1 ] *= 4;

			//final ArrayList<ViewSetup> viewSetups =
			//		N5ResaveTools.assembleViewSetups( data, vidsToResave );

			final HashMap<Integer, long[]> viewSetupIdToDimensions =
					N5ResaveTools.assembleDimensions( data, vidsToResave );

			IOFunctions.println( "Dimensions of raw images: " );
			viewSetupIdToDimensions.forEach( (id,dim ) -> IOFunctions.println( "ViewSetup " + id + ": " + Arrays.toString( dim )) );

			final int[][] downsamplings =
					N5ResaveTools.mipMapInfoToDownsamplings( n5Params.proposedMipmaps );

			IOFunctions.println( "Downsamplings: " + Arrays.deepToString( downsamplings ) );

			final ArrayList<long[][]> grid =
					N5ResaveTools.assembleAllS0Jobs( vidsToResave, viewSetupIdToDimensions, blockSize, computeBlockSize );

			N5ResaveTools.createGroups( n5Writer, data, viewSetupIdToDimensions, blockSize, downsamplings, compression );
			N5ResaveTools.createS0Datasets( n5Writer, vidsToResave, viewSetupIdToDimensions, blockSize, compression );

			//
			// Save full resolution dataset (s0)
			//
			final ForkJoinPool myPool = new ForkJoinPool( n5Params.numCellCreatorThreads );

			long time = System.currentTimeMillis();

			try
			{
				myPool.submit(() -> grid.parallelStream().forEach( gridBlock -> N5ResaveTools.writeS0Block( data, n5Writer, gridBlock ) ) ).get();
			}
			catch (InterruptedException | ExecutionException e)
			{
				IOFunctions.println( "Failed to write s0 for N5 '" + n5Params.n5URI + "'. Error: " + e );
				e.printStackTrace();
				return null;
			}

			IOFunctions.println( "Saved level s0, took: " + (System.currentTimeMillis() - time ) + " ms." );

			//
			// Save remaining downsampling levels (s1 ... sN)
			//

			for ( int level = 1; level < downsamplings.length; ++level )
			{
				final int s = level;
				final int[] ds = N5ResaveTools.computeRelativeDownsampling( downsamplings, s );
				IOFunctions.println( "Downsampling: " + Util.printCoordinates( downsamplings[ s ] ) + " with relative downsampling of " + Util.printCoordinates( ds ));

				final ArrayList<long[][]> allBlocks =
						N5ResaveTools.prepareDownsampling( vidsToResave, n5Writer, level, downsamplings[ s ], ds, blockSize, compression );

				time = System.currentTimeMillis();

				try
				{
					myPool.submit(() -> allBlocks.parallelStream().forEach(
							gridBlock -> N5ResaveTools.writeDownsampledBlock(
									n5Writer,
									N5ResaveTools.mappingFunctionBDV( s ),
									N5ResaveTools.mappingFunctionBDV( s - 1 ),
									ds,
									gridBlock ) ) ).get();
				}
				catch (InterruptedException | ExecutionException e)
				{
					IOFunctions.println( "Failed to write downsample step s" + s +" for N5 '" + n5Params.n5URI + "'. Error: " + e );
					e.printStackTrace();
					return null;
				}

				IOFunctions.println( "Resaved N5 s" + s + " level, took: " + (System.currentTimeMillis() - time ) + " ms." );
			}

			myPool.shutdown();
			try { myPool.awaitTermination( Long.MAX_VALUE, TimeUnit.HOURS ); } catch (InterruptedException e) { e.printStackTrace(); }

			n5Writer.close();
		}

		sdReduced.getSequenceDescription().setImgLoader( new N5ImageLoader( n5Params.n5URI, sdReduced.getSequenceDescription() ) );
		sdReduced.setBasePathURI( URITools.getParent( n5Params.xmlURI ) );

		if ( saveXML )
		{
			progressWriter.out().println( new Date( System.currentTimeMillis() ) + ": Saving " + n5Params.xmlURI );
			new XmlIoSpimData2().save( sdReduced, n5Params.xmlURI );
		}

		progressWriter.setProgress( 1.0 );
		progressWriter.out().println( new Date( System.currentTimeMillis() ) + ": Finished saving " + n5Params.n5URI );

		return sdReduced;
	}

	public static void main(String[] args)
	{
		List<long[][]> grid = Grid.create( new long[] { 500, 500 }, new int[] { 400, 400 }, new int[] { 200, 200 } );

		grid.forEach( b -> {
			System.out.println( Arrays.toString( b[0]));
			System.out.println( Arrays.toString( b[1]));
			System.out.println( Arrays.toString( b[2]));
			System.out.println();
		});
		//new ImageJ();
		//new Resave_N5().run( null );
	}
}
