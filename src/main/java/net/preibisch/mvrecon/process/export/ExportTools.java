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
package net.preibisch.mvrecon.process.export;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.n5.N5ImageLoader;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.export.ExportN5API.StorageType;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import util.URITools;

public class ExportTools {

	public static int[][] estimateMultiResPyramid( final Dimensions dimensions, final double aniso )
	{
		final VoxelDimensions v = new FinalVoxelDimensions( "px", 1.0, 1.0, Double.isNaN( aniso ) ? 1.0 : aniso );
		final BasicViewSetup setup = new BasicViewSetup(0, "fusion", dimensions, v );
		final ExportMipmapInfo emi = ProposeMipmaps.proposeMipmaps( setup );

		return emi.getExportResolutions();
	}

	public static boolean writeBDVMetaData(
			final N5Writer driverVolumeWriter,
			final StorageType storageType,
			final DataType dataType,
			final long[] dimensions,
			final Compression compression,
			final int[] blockSize,
			final int[][] downsamplings,
			final ViewId viewId,
			final URI n5PathURI,
			final URI xmlOutPathURI,
			final InstantiateViewSetup instantiateViewSetup ) throws SpimDataException, IOException
	{
		System.out.println( "Writing BDV-metadata ... " );

		//final String xmlPath = null;
		if ( StorageType.N5.equals(storageType) )
		{
			System.out.println( "XML: " + xmlOutPathURI );

			final Pair<Boolean, Boolean> exists = writeSpimData(
					viewId,
					storageType,
					dimensions,
					n5PathURI,
					xmlOutPathURI,
					instantiateViewSetup );

			if ( exists == null )
				return false;

			String ds = "setup" + viewId.getViewSetupId();

			// if viewsetup does not exist
			if ( !exists.getB() )
			{
				// set N5 attributes for setup
				// e.g. {"compression":{"type":"gzip","useZlib":false,"level":1},"downsamplingFactors":[[1,1,1],[2,2,1]],"blockSize":[128,128,32],"dataType":"uint16","dimensions":[512,512,86]}
				System.out.println( "setting attributes for '" + "setup" + viewId.getViewSetupId() + "'");
				driverVolumeWriter.setAttribute(ds, "dataType", dataType );
				driverVolumeWriter.setAttribute(ds, "blockSize", blockSize );
				driverVolumeWriter.setAttribute(ds, "dimensions", dimensions );
				driverVolumeWriter.setAttribute(ds, "compression", compression );

				if ( downsamplings == null || downsamplings.length == 0 )
					driverVolumeWriter.setAttribute(ds, "downsamplingFactors", new int[][] {{1,1,1}} );
				else
					driverVolumeWriter.setAttribute(ds, "downsamplingFactors", downsamplings );
			}

			// set N5 attributes for timepoint
			// e.g. {"resolution":[1.0,1.0,3.0],"saved_completely":true,"multiScale":true}
			ds ="setup" + viewId.getViewSetupId() + "/" + "timepoint" + viewId.getTimePointId();
			driverVolumeWriter.setAttribute(ds, "resolution", new double[] {1,1,1} );
			driverVolumeWriter.setAttribute(ds, "saved_completely", true );
			driverVolumeWriter.setAttribute(ds, "multiScale", downsamplings != null && downsamplings.length != 0 );

			if ( downsamplings == null || downsamplings.length == 0 )
			{
				// set additional N5 attributes for s0 dataset
				ds = ds + "/s0";
				driverVolumeWriter.createGroup( ds );
				driverVolumeWriter.setAttribute(ds, "downsamplingFactors", new int[] {1,1,1} );
			}
			else
			{
				for ( int level = 0; level < downsamplings.length; ++level )
				{
					// set additional N5 attributes for s0 ... sN datasets
					final String dsLevel = ds + "/s" + level;
					driverVolumeWriter.createGroup( dsLevel );
					driverVolumeWriter.setAttribute(dsLevel, "downsamplingFactors", downsamplings[ level ] );
				}
			}

			return true;
		}
		else if ( StorageType.HDF5.equals(storageType) )
		{
			System.out.println( "XML: " + xmlOutPathURI );

			final Pair<Boolean, Boolean> exists = writeSpimData(
					viewId,
					storageType,
					dimensions,
					n5PathURI,
					xmlOutPathURI,
					instantiateViewSetup );

			if ( exists == null )
				return false;

			// if viewsetup does not exist
			if ( !exists.getB() )
			{
				final Img<IntType> subdivisions;
				final Img<DoubleType> resolutions;

				if ( downsamplings == null || downsamplings.length == 0 )
				{
					subdivisions = ArrayImgs.ints( blockSize, new long[] { 3, 1 } ); // blocksize
					resolutions = ArrayImgs.doubles( new double[] { 1,1,1 }, new long[] { 3, 1 } ); // downsampling
				}
				else
				{
					final int[] blocksizes = new int[ 3 * downsamplings.length ];
					final double[] downsamples = new double[ 3 * downsamplings.length ];

					int i = 0;
					for ( int level = 0; level < downsamplings.length; ++level )
					{
						downsamples[ i ] = downsamplings[ level ][ 0 ];
						blocksizes[ i++ ] = blockSize[ 0 ];
						downsamples[ i ] = downsamplings[ level ][ 1 ];
						blocksizes[ i++ ] = blockSize[ 1 ];
						downsamples[ i ] = downsamplings[ level ][ 2 ];
						blocksizes[ i++ ] = blockSize[ 2 ];
					}

					subdivisions = ArrayImgs.ints( blocksizes, new long[] { 3, downsamplings.length } ); // blocksize
					resolutions = ArrayImgs.doubles( downsamples, new long[] { 3, downsamplings.length } ); // downsampling
				}
				
				driverVolumeWriter.createDataset(
						"s" + String.format("%02d", viewId.getViewSetupId()) + "/subdivisions",
						subdivisions.dimensionsAsLongArray(),// new long[] { 3, 1 },
						new int[] { (int)subdivisions.dimension( 0 ), (int)subdivisions.dimension( 1 ) }, //new int[] { 3, 1 },
						DataType.INT32,
						new RawCompression() );

				driverVolumeWriter.createDataset(
						"s" + String.format("%02d", viewId.getViewSetupId()) + "/resolutions",
						resolutions.dimensionsAsLongArray(),// new long[] { 3, 1 },
						new int[] { (int)resolutions.dimension( 0 ), (int)resolutions.dimension( 1 ) },//new int[] { 3, 1 },
						DataType.FLOAT64,
						new RawCompression() );

				N5Utils.saveBlock(subdivisions, driverVolumeWriter, "s" + String.format("%02d", viewId.getViewSetupId()) + "/subdivisions", new long[] {0,0,0} );
				N5Utils.saveBlock(resolutions, driverVolumeWriter, "s" + String.format("%02d", viewId.getViewSetupId()) + "/resolutions", new long[] {0,0,0} );
			}

			return true;
		}
		else
		{
			IOFunctions.println( "BDV-compatible dataset cannot be written for " + storageType + " (yet).");
			return false;
		}
	}

	public static Pair<Boolean, Boolean> writeSpimData(
			final ViewId viewId,
			final StorageType storageType,
			final long[] dimensions,
			final URI n5PathURI,
			final URI xmlOutPathURI,
			final InstantiateViewSetup instantiateViewSetup ) throws SpimDataException
	{
		SpimData2 existingSpimData;

		try
		{
			existingSpimData = new XmlIoSpimData2().load( xmlOutPathURI );
		}
		catch (Exception e )
		{
			existingSpimData = null;
		}

		if ( existingSpimData != null ) //xmlOutPath.exists() )
		{
			System.out.println( "XML exists. Parsing and adding.");

			boolean tpExists = false;
			boolean viewSetupExists = false;

			for ( final ViewDescription viewId2 : existingSpimData.getSequenceDescription().getViewDescriptions().values() )
			{
				/*
				// uncommented this because if you make a second timepoint and do not add missing views, they all exist already
				if ( viewId2.equals( viewId ) )
				{
					IOFunctions.println( "ViewId you specified (" + Group.pvid(viewId) + ") already exists in the XML, cannot continue." );
					return null;
				}
				*/

				if ( viewId2.getTimePointId() == viewId.getTimePointId() )
					tpExists = true;

				if ( viewId2.getViewSetupId() == viewId.getViewSetupId() )
				{
					viewSetupExists = true;

					// dimensions have to match
					if ( !Intervals.equalDimensions( new FinalDimensions( dimensions ), viewId2.getViewSetup().getSize() ) )
					{
						IOFunctions.println( "ViewSetup you specified ("  + Group.pvid(viewId) + ") already exists in the XML, but with different dimensions, cannot continue." );
						return null;
					}
				}
			}

			final List<ViewSetup> setups = new ArrayList<>( existingSpimData.getSequenceDescription().getViewSetups().values() );

			if ( !viewSetupExists )
				setups.add( instantiateViewSetup.instantiate( viewId, tpExists, new FinalDimensions( dimensions ), setups ) );

			final TimePoints timepoints;
			if ( !tpExists) {
				final List<TimePoint> tps = new ArrayList<>(existingSpimData.getSequenceDescription().getTimePoints().getTimePointsOrdered());
				tps.add(new TimePoint(viewId.getTimePointId()));
				timepoints = new TimePoints(tps);
			}
			else
			{
				timepoints = existingSpimData.getSequenceDescription().getTimePoints();
			}

			final Map<ViewId, ViewRegistration> registrations = existingSpimData.getViewRegistrations().getViewRegistrations();
			registrations.put( viewId, new ViewRegistration( viewId.getTimePointId(), viewId.getViewSetupId() ) );
			final ViewRegistrations viewRegistrations = new ViewRegistrations( registrations );

			final SequenceDescription sequence = new SequenceDescription(timepoints, setups, null);

			if ( StorageType.N5.equals(storageType) )
				sequence.setImgLoader( new N5ImageLoader( n5PathURI, sequence) );
			else if ( StorageType.HDF5.equals(storageType) )
				sequence.setImgLoader( new Hdf5ImageLoader( new File( URITools.removeFilePrefix( n5PathURI ) ), null, sequence) );
			else
				throw new RuntimeException( storageType + " not supported." );

			final SpimData2 spimDataNew =
					new SpimData2(
							existingSpimData.getBasePathURI(),
							sequence,
							viewRegistrations,
							existingSpimData.getViewInterestPoints(),
							existingSpimData.getBoundingBoxes(),
							existingSpimData.getPointSpreadFunctions(),
							existingSpimData.getStitchingResults(),
							existingSpimData.getIntensityAdjustments() );

			new XmlIoSpimData2().save( spimDataNew, existingSpimData.getBasePathURI() );

			return new ValuePair<>(tpExists, viewSetupExists);
		}
		else
		{
			System.out.println( "New XML.");

			final ArrayList< ViewSetup > setups = new ArrayList<>();

			setups.add( instantiateViewSetup.instantiate( viewId, false, new FinalDimensions( dimensions ), setups ) );
			/*
			final Channel c0 = new Channel( 0 );
			final Angle a0 = new Angle( 0 );
			final Illumination i0 = new Illumination( 0 );
			final Tile t0 = new Tile( 0 );

			final Dimensions d0 = new FinalDimensions( dimensions );
			final VoxelDimensions vd0 = new FinalVoxelDimensions( "px", 1, 1, 1 );
			setups.add( new ViewSetup( viewId.getViewSetupId(), "setup " + viewId.getViewSetupId(), d0, vd0, t0, c0, a0, i0 ) );*/

			final ArrayList< TimePoint > tps = new ArrayList<>();
			tps.add( new TimePoint( viewId.getTimePointId() ) );
			final TimePoints timepoints = new TimePoints( tps );

			final HashMap< ViewId, ViewRegistration > registrations = new HashMap<>();
			registrations.put( viewId, new ViewRegistration( viewId.getTimePointId(), viewId.getViewSetupId() ) );
			final ViewRegistrations viewRegistrations = new ViewRegistrations( registrations );

			final SequenceDescription sequence = new SequenceDescription(timepoints, setups, null);
			if ( StorageType.N5.equals(storageType) )
				sequence.setImgLoader( new N5ImageLoader( n5PathURI, sequence) );
			else if ( StorageType.HDF5.equals(storageType) )
				sequence.setImgLoader( new Hdf5ImageLoader( new File( URITools.removeFilePrefix( n5PathURI ) ), null, sequence) );
			else
				throw new RuntimeException( storageType + " not supported." );

			final SpimData2 spimData = new SpimData2( xmlOutPathURI, sequence, viewRegistrations, new ViewInterestPoints(), new BoundingBoxes(), new PointSpreadFunctions(), new StitchingResults(), new IntensityAdjustments() );

			new XmlIoSpimData2().save( spimData, xmlOutPathURI );

			return new ValuePair<>(false, false);
		}
		
	}

	public static ViewId getViewId(final String bdvString )
	{
		final String[] entries = bdvString.trim().split( "," );
		final int timepointId = Integer.parseInt( entries[ 0 ].trim() );
		final int viewSetupId = Integer.parseInt( entries[ 1 ].trim() );

		return new ViewId(timepointId, viewSetupId);
	}

	public static String createBDVPath(final String bdvString, final StorageType storageType)
	{
		return createBDVPath(getViewId(bdvString), storageType);
	}

	public static String createBDVPath( final ViewId viewId, final StorageType storageType)
	{
		String path = null;

		if ( StorageType.N5.equals(storageType) )
		{
			path = "setup" + viewId.getViewSetupId() + "/" + "timepoint" + viewId.getTimePointId() + "/s0";
		}
		else if ( StorageType.HDF5.equals(storageType) )
		{
			path = "t" + String.format("%05d", viewId.getTimePointId()) + "/" + "s" + String.format("%02d", viewId.getViewSetupId()) + "/0/cells";
		}
		else
		{
			new RuntimeException( "BDV-compatible dataset cannot be written for " + storageType + " (yet).");
		}

		System.out.println( "Saving BDV-compatible " + storageType + " using ViewSetupId=" + viewId.getViewSetupId() + ", TimepointId=" + viewId.getTimePointId()  );
		System.out.println( "path=" + path );

		return path;
	}

	public static String createDownsampledBDVPath( final String s0path, final int level, final StorageType storageType )
	{
		if ( StorageType.N5.equals(storageType) )
		{
			return s0path.substring( 0, s0path.length() - 3 ) + "/s" + level;
		}
		else if ( StorageType.HDF5.equals(storageType) )
		{
			return s0path.substring( 0, s0path.length() - 8 ) + "/" + level + "/cells";
		}
		else
		{
			throw new RuntimeException( "BDV-compatible dataset cannot be written for " + storageType + " (yet).");
		}
	}

	@FunctionalInterface
	public static interface InstantiateViewSetup
	{
		public ViewSetup instantiate( final ViewId viewId, final boolean tpExists, final Dimensions d, final List<ViewSetup> existingSetups );
	}

	public static class InstantiateViewSetupBigStitcher implements InstantiateViewSetup
	{
		final int splittingType;

		// count the fusion groups
		int count = 0;

		// new indicies
		int newA = -1;
		int newC = -1;
		int newI = -1;
		int newT = -1;

		public InstantiateViewSetupBigStitcher(
				final int splittingType)
		{
			this.splittingType = splittingType;
		}

		@Override
		public ViewSetup instantiate( final ViewId viewId, final boolean tpExists, final Dimensions d, final List<ViewSetup> existingSetups )
		{
			if ( existingSetups == null || existingSetups.size() == 0 )
			{
				newA = 0;
				newC = 0;
				newI = 0;
				newT = 0;
				
			}
			else
			{
				final Iterator<ViewSetup> i = existingSetups.iterator();
				ViewSetup tmp = i.next();
	
				Channel c0 = tmp.getChannel();
				Angle a0 = tmp.getAngle();
				Illumination i0 = tmp.getIllumination();
				Tile t0 = tmp.getTile();
	
				// get the highest id for all entities
				while ( i.hasNext() )
				{
					tmp = i.next();
					if ( tmp.getChannel().getId() > c0.getId() )
						c0 = tmp.getChannel();
					if ( tmp.getAngle().getId() > a0.getId() )
						a0 = tmp.getAngle();
					if ( tmp.getIllumination().getId() > i0.getId() )
						i0 = tmp.getIllumination();
					if ( tmp.getTile().getId() > t0.getId() )
						t0 = tmp.getTile();
				}
	
				// new unique id's for all, initialized once
				if ( newA < 0 )
				{
					newA = a0.getId() + 1;
					newC = c0.getId() + 1;
					newI = i0.getId() + 1;
					newT = t0.getId() + 1;
				}
			}

			Angle a0;
			Channel c0;
			Illumination i0;
			Tile t0;

			// 0 == "Each timepoint &amp; channel",
			// 1 == "Each timepoint, channel &amp; illumination",
			// 2 == "All views together",
			// 3 == "Each view"
			if ( splittingType == 0 )
			{
				// a new channel for each fusion group
				c0 = new Channel( newC + count );

				a0 = new Angle( newA );
				i0 = new Illumination( newI );
				t0 = new Tile( newT );
			}
			else if ( splittingType == 1 )
			{
				// TODO: we need to know what changed
				// a new channel and illumination for each fusion group
				c0 = new Channel( newC + count );
				i0 = new Illumination( newI + count );

				a0 = new Angle( newA );
				t0 = new Tile( newT );
			}
			else if ( splittingType == 2 )
			{
				// a new channel, angle, tile and illumination for the single fusion group
				a0 = new Angle( newA );
				c0 = new Channel( newC );
				i0 = new Illumination( newI );
				t0 = new Tile( newT );
			}
			else if ( splittingType == 3 )
			{
				// TODO: use previous ones
				// TODO: we need to know what changed
				c0 = new Channel( newC + count );
				i0 = new Illumination( newI + count );
				a0 = new Angle( newA + count );
				t0 = new Tile( newT + count );
			}
			else
			{
				IOFunctions.println( "SplittingType " + splittingType + " unknown. Stopping.");
				return null;
			}

			final VoxelDimensions vd0 = new FinalVoxelDimensions( "px", 1, 1, 1 );

			++count;

			return new ViewSetup( viewId.getViewSetupId(), "setup " + viewId.getViewSetupId(), d, vd0, t0, c0, a0, i0 );
		}
	}

	public static class InstantiateViewSetupBigStitcherSpark implements InstantiateViewSetup
	{
		final String angleIds;
		final String illuminationIds;
		final String channelIds;
		final String tileIds;

		public InstantiateViewSetupBigStitcherSpark(
				final String angleIds,
				final String illuminationIds,
				final String channelIds,
				final String tileIds )
		{
			this.angleIds = angleIds;
			this.illuminationIds = illuminationIds;
			this.channelIds = channelIds;
			this.tileIds = tileIds;
		}

		@Override
		public ViewSetup instantiate( final ViewId viewId, final boolean tpExists, final Dimensions d, final List<ViewSetup> existingSetups )
		{
			Angle a0;
			Channel c0;
			Illumination i0;
			Tile t0;

			if ( existingSetups == null || existingSetups.size() == 0 )
			{
				a0 = new Angle( 0 );
				c0 = new Channel( 0 );
				i0 = new Illumination( 0 );
				t0 = new Tile( 0 );
			}
			else
			{
				final Iterator<ViewSetup> i = existingSetups.iterator();
				ViewSetup tmp = i.next();
	
				c0 = tmp.getChannel();
				a0 = tmp.getAngle();
				i0 = tmp.getIllumination();
				t0 = tmp.getTile();

				// get the highest id for all entities
				while ( i.hasNext() )
				{
					tmp = i.next();
					if ( tmp.getChannel().getId() > c0.getId() )
						c0 = tmp.getChannel();
					if ( tmp.getAngle().getId() > a0.getId() )
						a0 = tmp.getAngle();
					if ( tmp.getIllumination().getId() > i0.getId() )
						i0 = tmp.getIllumination();
					if ( tmp.getTile().getId() > t0.getId() )
						t0 = tmp.getTile();
				}

				if ( angleIds != null )
					a0 = new Angle( a0.getId() + 1 );
				if ( illuminationIds != null )
					i0 = new Illumination( i0.getId() + 1 );
				if ( tileIds != null )
					t0 = new Tile( t0.getId() + 1 );
				if ( tileIds != null || ( angleIds == null && illuminationIds == null && tileIds == null && tpExists ) ) // nothing was defined, then increase channel
					c0 = new Channel( c0.getId() + 1 );
			}

			//final Dimensions d0 = new FinalDimensions( dimensions );
			final VoxelDimensions vd0 = new FinalVoxelDimensions( "px", 1, 1, 1 );

			return new ViewSetup( viewId.getViewSetupId(), "setup " + viewId.getViewSetupId(), d, vd0, t0, c0, a0, i0 );
		}
	}
}
