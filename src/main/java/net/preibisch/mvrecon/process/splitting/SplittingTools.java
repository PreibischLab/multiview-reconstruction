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
package net.preibisch.mvrecon.process.splitting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import bdv.ViewerImgLoader;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
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
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.iterator.LocalizingZeroMinIntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitMultiResolutionImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

public class SplittingTools
{
	public static SpimData2 splitImages( final SpimData2 spimData, final long[] overlapPx, final long[] targetSize )
	{
		final TimePoints timepoints = spimData.getSequenceDescription().getTimePoints();

		final List< ViewSetup > oldSetups = new ArrayList<>();
		oldSetups.addAll( spimData.getSequenceDescription().getViewSetups().values() );
		Collections.sort( oldSetups );

		final ViewRegistrations oldRegistrations = spimData.getViewRegistrations();

		final ImgLoader underlyingImgLoader = spimData.getSequenceDescription().getImgLoader();
		spimData.getSequenceDescription().setImgLoader( null ); // we don't need it anymore there as we save it later

		final HashMap< Integer, Integer > new2oldSetupId = new HashMap<>();
		final HashMap< Integer, Interval > newSetupId2Interval = new HashMap<>();

		final ArrayList< ViewSetup > newSetups = new ArrayList<>();
		final Map< ViewId, ViewRegistration > newRegistrations = new HashMap<>();
		final Map< ViewId, ViewInterestPointLists > newInterestpoints = new HashMap<>();

		int newId = 0;

		// new tileId is locally computed based on the old tile ids
		// by multiplying it with maxspread and then +1 for each new tile
		// so each new one has to be the same across channel & illumination!
		final int maxIntervalSpread = maxIntervalSpread( oldSetups, overlapPx, targetSize );

		for ( final ViewSetup oldSetup : oldSetups )
		{
			final int oldID = oldSetup.getId();
			final Tile oldTile = oldSetup.getTile();
			int localNewTileId = 0;

			final Angle angle = oldSetup.getAngle();
			final Channel channel = oldSetup.getChannel();
			final Illumination illum = oldSetup.getIllumination();
			final VoxelDimensions voxDim = oldSetup.getVoxelSize();

			final Interval input = new FinalInterval( oldSetup.getSize() );
			final ArrayList< Interval > intervals = distributeIntervalsFixedOverlap( input, overlapPx, targetSize );

			for ( int i = 0; i < intervals.size(); ++i )
			{
				final Interval interval = intervals.get( i );

				// from the new ID get the old ID and the corresponding interval
				new2oldSetupId.put( newId, oldID );
				newSetupId2Interval.put( newId, interval );

				final long[] size = new long[ interval.numDimensions() ];
				interval.dimensions( size );
				final Dimensions newDim = new FinalDimensions( size );

				final double[] location = oldTile.getLocation() == null ? new double[ interval.numDimensions() ] : oldTile.getLocation().clone();
				for ( int d = 0; d < interval.numDimensions(); ++d )
					location[ d ] += interval.min( d );

				final int newTileId = oldTile.getId() * maxIntervalSpread + localNewTileId;
				localNewTileId++;
				final Tile newTile = new Tile( newTileId, Integer.toString( newTileId ), location );
				final ViewSetup newSetup = new ViewSetup( newId, null, newDim, voxDim, newTile, channel, angle, illum );
				newSetups.add( newSetup );

				// update registrations and interest points for all timepoints
				for ( final TimePoint t : timepoints.getTimePointsOrdered() )
				{
					final ViewId oldViewId = new ViewId( t.getId(), oldSetup.getId() );
					final ViewRegistration oldVR = oldRegistrations.getViewRegistration( oldViewId );
					final ArrayList< ViewTransform > transformList = new ArrayList<>( oldVR.getTransformList() );

					final AffineTransform3D translation = new AffineTransform3D();
					translation.set( 1.0f, 0.0f, 0.0f, interval.min( 0 ),
							0.0f, 1.0f, 0.0f, interval.min( 1 ),
							0.0f, 0.0f, 1.0f, interval.min( 2 ) );

					final ViewTransformAffine transform = new ViewTransformAffine( "Image Splitting", translation );
					transformList.add( transform );

					final ViewId newViewId = new ViewId( t.getId(), newSetup.getId() );
					final ViewRegistration newVR = new ViewRegistration( newViewId.getTimePointId(), newViewId.getViewSetupId(), transformList );
					newRegistrations.put( newViewId, newVR );

					// Interest points
					final ViewInterestPointLists newVipl = new ViewInterestPointLists( newViewId.getTimePointId(), newViewId.getViewSetupId() );
					final ViewInterestPointLists oldVipl = spimData.getViewInterestPoints().getViewInterestPointLists( oldViewId );

					// only update interest points for present views
					// oldVipl may be null for missing views
					if (!spimData.getSequenceDescription().getMissingViews().getMissingViews().contains( oldViewId ) )
					{
						for ( final String label : oldVipl.getHashMap().keySet() )
						{
							final InterestPointList oldIpl = oldVipl.getInterestPointList( label );
							final List< InterestPoint > oldIp = oldIpl.getInterestPointsCopy();
							final ArrayList< InterestPoint > newIp = new ArrayList<>();
	
							int id = 0;
							for ( final InterestPoint ip : oldIp )
							{
								if ( contains( ip.getL(), interval ) )
								{
									final double[] l = ip.getL();
									for ( int d = 0; d < interval.numDimensions(); ++d )
										l[ d ] -= interval.min( d );
	
									newIp.add( new InterestPoint( id++, l ) );
								}
							}
		
							final InterestPointList newIpl = new InterestPointList( oldIpl.getBaseDir(), new File(
									"interestpoints", "new_tpId_" + newViewId.getTimePointId() +
									"_viewSetupId_" + newViewId.getViewSetupId() + "." + label ) );
							newIpl.setInterestPoints( newIp );
							newVipl.addInterestPointList( label, newIpl ); // still add
						}
					}
					newInterestpoints.put( newViewId, newVipl );
				}

				newId++;
			}
		}

		// missing views
		final MissingViews oldMissingViews = spimData.getSequenceDescription().getMissingViews();
		final HashSet< ViewId > missingViews = new HashSet< ViewId >();

		if ( oldMissingViews != null && oldMissingViews.getMissingViews() != null )
			for ( final ViewId id : oldMissingViews.getMissingViews() )
				for ( final int newSetupId : new2oldSetupId.keySet() )
					if ( new2oldSetupId.get( newSetupId ) == id.getViewSetupId() )
						missingViews.add( new ViewId( id.getTimePointId(), newSetupId ) );

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, newSetups, null, new MissingViews( missingViews ) );
		final ImgLoader imgLoader;

		if ( ViewerImgLoader.class.isInstance( underlyingImgLoader ) )
		{
			imgLoader = new SplitViewerImgLoader( (ViewerImgLoader)underlyingImgLoader, new2oldSetupId, newSetupId2Interval, spimData.getSequenceDescription() );
		}
		else if ( MultiResolutionImgLoader.class.isInstance( underlyingImgLoader ) )
		{
			imgLoader = new SplitMultiResolutionImgLoader( (MultiResolutionImgLoader)underlyingImgLoader, new2oldSetupId, newSetupId2Interval, spimData.getSequenceDescription()  );
		}
		else
		{
			imgLoader = new SplitImgLoader( underlyingImgLoader, new2oldSetupId, newSetupId2Interval, spimData.getSequenceDescription()  );
		}

		sequenceDescription.setImgLoader( imgLoader );

		// interest points
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints( newInterestpoints );

		// view registrations
		final ViewRegistrations viewRegistrations = new ViewRegistrations( newRegistrations );

		// add point spread functions
		final HashMap< ViewId, PointSpreadFunction > newPsfs = new HashMap<>();

		/*
		final HashMap< ViewId, PointSpreadFunction > oldPsfs = spimData.getPointSpreadFunctions().getPointSpreadFunctions();

		for ( final ViewDescription newViewId : sequenceDescription.getViewDescriptions().values() )
		{
			if ( newViewId.isPresent() )
			{
				final ViewId oldViewId = new ViewId( newViewId.getTimePointId(), new2oldSetupId.get( newViewId.getViewSetupId() ) );
				if ( oldPsfs.containsKey( oldViewId ) )
				{
					final PointSpreadFunction oldPsf = oldPsfs.get( oldViewId );
					final Img< FloatType > img = oldPsf.getPSFCopy();
					final PointSpreadFunction newPsf = new PointSpreadFunction( spimData.getBasePath(), PointSpreadFunction.createPSFFileName( newViewId ), img );
					newPsfs.put( newViewId, newPsf );
				}
			}
		}*/

		final PointSpreadFunctions psfs = new PointSpreadFunctions( newPsfs );

		// TODO: fix intensity adjustments?

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimDataNew = new SpimData2( spimData.getBasePath(), sequenceDescription, viewRegistrations, viewInterestPoints, spimData.getBoundingBoxes(), psfs, new StitchingResults(), new IntensityAdjustments() );

		return spimDataNew;
	}

	private static final int maxIntervalSpread( final List< ViewSetup > oldSetups, final long[] overlapPx, final long[] targetSize )
	{
		int max = 1;

		for ( final ViewSetup oldSetup : oldSetups )
		{
			final Interval input = new FinalInterval( oldSetup.getSize() );
			final ArrayList< Interval > intervals = distributeIntervalsFixedOverlap( input, overlapPx, targetSize );

			max = Math.max( max, intervals.size() );
		}

		return max;
	}
	private static final boolean contains( final double[] l, final Interval interval )
	{
		for ( int d = 0; d < l.length; ++d )
			if ( l[ d ] < interval.min( d ) || l[ d ] > interval.max( d ) )
				return false;

		return true;
	}

	public static ArrayList< Interval > distributeIntervalsFixedOverlap( final Interval input, final long[] overlapPx, final long[] targetSize )
	{
		final ArrayList< ArrayList< Pair< Long, Long > > > intervalBasis = new ArrayList<>();

		for ( int d = 0; d < input.numDimensions(); ++d )
		{
			final ArrayList< Pair< Long, Long > > dimIntervals = new ArrayList<>();
	
			final long length = input.dimension( d );

			// can I use just 1 block?
			if ( length <= targetSize[ d ] )
			{
				final long min = input.min( d );
				final long max = input.max( d );

				dimIntervals.add( new ValuePair< Long, Long >( min, max ) );
				System.out.println( "one block from " + min + " to " + max );
			}
			else
			{
				final double l = length;
				final double s = targetSize[ d ];
				final double o = overlapPx[ d ];
	
				final double numCenterBlocks = ( l - 2.0 * ( s-o ) - o ) / ( s - 2.0 * o + o );
				final long numCenterBlocksInt;

				if ( numCenterBlocks <= 0.0 )
					numCenterBlocksInt = 0;
				else
					numCenterBlocksInt = Math.round( numCenterBlocks );

				final double n = numCenterBlocksInt;

				final double newSize = ( l + o + n * o ) / ( 2.0 + n );
				final long newSizeInt = Math.round( newSize );

				System.out.println( "numCenterBlocks: " + numCenterBlocks );
				System.out.println( "numCenterBlocksInt: " + numCenterBlocksInt );
				System.out.println( "numBlocks: " + (numCenterBlocksInt + 2) );
				System.out.println( "newSize: " + newSize );
				System.out.println( "newSizeInt: " + newSizeInt );

				System.out.println();
				//System.out.println( "block 0: " + input.min( d ) + " " + (input.min( d ) + Math.round( newSize ) - 1) );

				for ( int i = 0; i <= numCenterBlocksInt; ++i )
				{
					final long from = Math.round( input.min( d ) + i * newSize - i * o );
					final long to = from + newSizeInt - 1;

					System.out.println( "block " + (numCenterBlocksInt) + ": " + from + " " + to );
					dimIntervals.add( new ValuePair< Long, Long >( from, to ) );
				}

				final long from = ( input.max( d ) - Math.round( newSize ) + 1 );
				final long to = input.max( d );
	
				System.out.println( "block " + (numCenterBlocksInt + 1) + ": " + from + " " + to );
				dimIntervals.add( new ValuePair< Long, Long >( from, to ) );
			}

			intervalBasis.add( dimIntervals );
		}

		final long[] numIntervals = new long[ input.numDimensions() ];

		for ( int d = 0; d < input.numDimensions(); ++d )
			numIntervals[ d ] = intervalBasis.get( d ).size();

		final LocalizingZeroMinIntervalIterator cursor = new LocalizingZeroMinIntervalIterator( numIntervals );
		final ArrayList< Interval > intervalList = new ArrayList<>();

		final int[] currentInterval = new int[ input.numDimensions() ];

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.localize( currentInterval );

			final long[] min = new long[ input.numDimensions() ];
			final long[] max = new long[ input.numDimensions() ];

			for ( int d = 0; d < input.numDimensions(); ++d )
			{
				final Pair< Long, Long > minMax = intervalBasis.get( d ).get( currentInterval[ d ] );
				min[ d ] = minMax.getA();
				max[ d ] = minMax.getB();
			}

			intervalList.add( new FinalInterval( min, max ) );
		}

		return intervalList;
	}

	public static void main( String[] args )
	{
		Interval input = new FinalInterval( new long[]{ 0 }, new long[] { 1915 - 1 } );
		long[] overlapPx = new long[] { 10 };
		long[] targetSize = new long[] { 500 };

		ArrayList< Interval > intervals = distributeIntervalsFixedOverlap( input, overlapPx, targetSize );

		System.out.println();

		for ( final Interval interval : intervals )
			System.out.println( Util.printInterval( interval ) );
	}
}
