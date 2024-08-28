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
package net.preibisch.mvrecon.process.splitting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.iterator.LocalizingZeroMinIntervalIterator;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.Split_Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitMultiResolutionImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

public class SplittingTools
{
	//public static boolean assingIlluminationsFromTileIds = false;
	//public static double error = 0.5;
	//public static int minPoints = 20;
	//public static int maxPoints = 500;

	/**
	 * 
	 * @param spimData - the spimdata object to split up
	 * @param overlapPx - the expected overlap
	 * @param targetSize - roughly the expected size of each subdivided tile
	 * @param minStepSize - step size or multipleOf for coordinates and sizes (except size of last tile), e.g. 8 - defined by the lowest downsampling step
	 * @param assingIlluminationsFromTileIds - use illumination attribute to remember former tiles
	 * @param optimize - whether to optimize overlap size
	 * @param addIPs - if fake interest points should be added
	 * @param pointDensity - how many points per 100x100x100 volume
	 * @param minPoints - min number of generated fake points per pair
	 * @param maxPoints - max number of generated fake points per pair
	 * @param error - artifical error for matching points
	 * @param excludeRadius - the radius around existing points in which fake points cannot be put
	 * @return
	 */
	public static SpimData2 splitImages(
			final SpimData2 spimData,
			final long[] overlapPx,
			final long[] targetSize,
			final long[] minStepSize,
			final boolean assingIlluminationsFromTileIds,
			final boolean optimize,
			final boolean addIPs,
			final double pointDensity,
			final int minPoints,
			final int maxPoints,
			final double error,
			final double excludeRadius )
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
		final int maxIntervalSpread = maxIntervalSpread( oldSetups, overlapPx, targetSize, minStepSize, optimize );

		// check that there is only one illumination
		if ( assingIlluminationsFromTileIds )
			if ( spimData.getSequenceDescription().getAllIlluminationsOrdered().size() > 1 )
				throw new IllegalArgumentException( "Cannot SplittingTools.assingIlluminationsFromTileIds because more than one Illumination exists." );

		// only relevant if addIPs is selected
		final String fakeLabel = "splitPoints_" + System.currentTimeMillis();

		final Random rnd = new Random( 23424459 );

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

			IOFunctions.println( "ViewId " + oldSetup.getId() + " with interval " + Util.printInterval( input ) + " will be split as follows: " );

			final ArrayList< Interval > intervals = distributeIntervalsFixedOverlap( input, overlapPx, targetSize, minStepSize, optimize );

			final HashMap< Interval, ViewSetup > interval2ViewSetup = new HashMap<>();

			for ( int i = 0; i < intervals.size(); ++i )
			{
				final Interval interval = intervals.get( i );

				IOFunctions.println( "Interval " + (i+1) + ": " + Util.printInterval( interval ) );
				//System.out.println( "Interval " + i + ": " + Util.printInterval( interval ) );

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
				final Illumination newIllum = assingIlluminationsFromTileIds ? new Illumination( oldTile.getId(), "old_tile_" + oldTile.getId() ) : illum;
				final ViewSetup newSetup = new ViewSetup( newId, null, newDim, voxDim, newTile, channel, angle, newIllum );
				newSetups.add( newSetup );

				interval2ViewSetup.put( interval, newSetup );

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
					if (spimData.getSequenceDescription().getMissingViews() != null && !spimData.getSequenceDescription().getMissingViews().getMissingViews().contains( oldViewId ) )
					{
						for ( final String label : oldVipl.getHashMap().keySet() )
						{
							int id = 0;

							final ArrayList< InterestPoint > newIp1 = new ArrayList<>();
							final InterestPoints oldIpl1 = oldVipl.getInterestPointList( label );
							final List< InterestPoint > oldIp1 = oldIpl1.getInterestPointsCopy();

							for ( final InterestPoint ip : oldIp1 )
							{
								if ( contains( ip.getL(), interval ) )
								{
									final double[] l = ip.getL().clone();
									for ( int d = 0; d < interval.numDimensions(); ++d )
										l[ d ] -= interval.min( d );// + (rnd.nextDouble() - 0.5);
	
									newIp1.add( new InterestPoint( id++, l ) );
								}
							}

							final InterestPoints newIpl1 = InterestPoints.newInstance( oldIpl1.getBaseDir(), newViewId, label + "_split" );
							newIpl1.setInterestPoints( newIp1 );
							newIpl1.setParameters( oldIpl1.getParameters() );
							newIpl1.setCorrespondingInterestPoints( new ArrayList<>() );
							newVipl.addInterestPointList( label + "_split", newIpl1 ); // still add
						}

						// adding random corresponding interest points in overlapping areas of introduced split views
						if ( addIPs )
						{
							final ArrayList< InterestPoint > newIp = new ArrayList<>();
							int id = 0;

							// for each overlapping tile that has not been processed yet
							for ( int j = 0; j < i; ++j )
							{
								final Interval otherInterval = intervals.get( j );
								final Interval intersection = Intervals.intersect( interval, otherInterval );

								//System.out.println( "vs. interval " + j + ": " + Util.printInterval( otherInterval ));
								//System.out.println( "error: " + error );

								// find the overlap
								if ( !Intervals.isEmpty( intersection ) )
								{
									final ViewSetup otherSetup = interval2ViewSetup.get( otherInterval );
									final ViewId otherViewId = new ViewId( t.getId(), otherSetup.getId() );
									final ViewInterestPointLists otherIPLists = newInterestpoints.get( otherViewId );

									//System.out.println( "Intersection between " + Util.printInterval( interval ) + " & " + Util.printInterval( otherInterval ) + ":");
									//System.out.println( Util.printInterval( intersection ) );

									// add points as function of the area
									final int n = intersection.numDimensions();
									long numPixels = 1;
									for ( int d = 0; d < n; ++d )
										numPixels *= intersection.dimension( d );

									final int numPoints = Math.min( maxPoints, Math.max( minPoints, (int)Math.round( Math.ceil( pointDensity * numPixels / (100.0*100.0*100.0) ) ) ) );
									System.out.println(numPixels / (100.0*100.0*100.0) + " " + numPoints  );

									final List< InterestPoint > otherPoints = otherIPLists.getInterestPointList( fakeLabel ).getInterestPointsCopy();
									int otherId = otherPoints.size() > 0 ? otherPoints.get( otherPoints.size() - 1 ).getId() + 1 : 0;

									// find the area that does not contain interest points yet
									final KDTree< InterestPoint > tree2;
									final RadiusNeighborSearch< InterestPoint > search2;

									if ( excludeRadius > 0 )
									{
										// build a tree that contains new added interest points
										final List< InterestPoint > otherIPglobal = new ArrayList<>();
										for ( final InterestPoint ip : otherPoints )
										{
											final double[] l = ip.getL().clone();
											for ( int d = 0; d < n; ++d )
												l[ d ] += otherInterval.min( d );

											otherIPglobal.add( new InterestPoint( ip.getId(), l ) );
										}

										if ( otherIPglobal.size() > 0 )
										{
											tree2 = new KDTree<>( otherIPglobal, otherIPglobal );
											search2 = new RadiusNeighborSearchOnKDTree<>( tree2 );
										}
										else
										{
											tree2 = null;
											search2 = null;
										}
									}
									else
									{
										tree2  = null;
										search2 = null;
									}

									final double[] tmp = new double[ n ];

									for ( int k = 0; k < numPoints; ++k )
									{
										final double[] p = new double[ n ];
										final double[] op = new double[ n ];

										for ( int d = 0; d < n; ++d )
										{
											final double l = rnd.nextDouble() * intersection.dimension( d ) + intersection.min( d );
											p[ d ] = (l + (rnd.nextDouble()-0.5)*error ) - interval.min( d );
											op[ d ] = (l + (rnd.nextDouble()-0.5)*error ) - otherInterval.min( d );
											tmp[ d ] = l;
										}
										//System.out.println( Arrays.toString( tmp ) + ", " + Arrays.toString( op ));

										int numNeighbors = 0;

										if ( excludeRadius > 0 )
										{
											final InterestPoint tmpIP = new InterestPoint( 0, tmp );
											if ( search2 != null )
											{
												search2.search( tmpIP, excludeRadius, false);
												numNeighbors += search2.numNeighbors();
											}
										}

										// if it's not too close to other points add the same point to both overlapping split tiles
										if ( numNeighbors == 0 )
										{
											newIp.add( new InterestPoint( id++, p ) );
											otherPoints.add( new InterestPoint( otherId++, op ) );
										}
									}

									otherIPLists.getInterestPointList( fakeLabel ).setInterestPoints( otherPoints );
								}
							}

							final InterestPoints newIpl = InterestPoints.newInstance( spimData.getBasePathURI(), newViewId, fakeLabel );
							newIpl.setInterestPoints( newIp );
							newIpl.setParameters(
									"Fake points for image splitting: overlapPx=" + Arrays.toString( overlapPx ) +
									", targetSize=" + Arrays.toString( targetSize ) +
									", minStepSize=" + Arrays.toString( minStepSize ) +
									", optimize=" + optimize +
									", pointDensity=" + pointDensity +
									", minPoints=" + minPoints +
									", maxPoints=" + maxPoints +
									", error=" + error +
									", excludeRadius=" + excludeRadius );
							newIpl.setCorrespondingInterestPoints( new ArrayList<>() );
							newVipl.addInterestPointList( fakeLabel, newIpl ); // still add
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

	private static final int maxIntervalSpread( final List< ViewSetup > oldSetups, final long[] overlapPx, final long[] targetSize, final long[] minStepSize, final boolean optimize  )
	{
		int max = 1;

		for ( final ViewSetup oldSetup : oldSetups )
		{
			final Interval input = new FinalInterval( oldSetup.getSize() );
			final ArrayList< Interval > intervals = distributeIntervalsFixedOverlap( input, overlapPx, targetSize, minStepSize, optimize );

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

	/**
	 * computes a set of overlapping intervals with desired target size and overlap. Importantly, minStepSize is computed from the multi-resolution pyramid and constrains 
	 * that intervals need to be divisible by minStepSize (except the last one) AND that the offsets where images start are divisble by minStepSize.
	 * 
	 * Otherwise one would need to recompute the multi-resolution pyramid.
	 * 
	 * @param input
	 * @param overlapPx
	 * @param targetSize
	 * @param minStepSize
	 * @param optimize - optimize targetsize to make tiles as equal as possible
	 * @return
	 */
	public static ArrayList< Interval > distributeIntervalsFixedOverlap( final Interval input, final long[] overlapPx, final long[] targetSize, final long[] minStepSize, final boolean optimize )
	{
		for ( int d = 0; d < input.numDimensions(); ++d )
		{
			if ( targetSize[ d ] % minStepSize[ d ] != 0 )
			{
				IOFunctions.printErr( "targetSize " + targetSize[ d ] + " not divisible by minStepSize " + minStepSize[ d ] + " for dim=" + d + ". stopping." );
				return null;
			}

			if ( overlapPx[ d ] % minStepSize[ d ] != 0 )
			{
				IOFunctions.printErr( "overlapPx " + overlapPx[ d ] + " not divisible by minStepSize " + minStepSize[ d ] + " for dim=" + d + ". stopping." );
				return null;
			}
		}

		final ArrayList< ArrayList< Pair< Long, Long > > > intervalBasis = new ArrayList<>();

		for ( int d = 0; d < input.numDimensions(); ++d )
		{
			//System.out.println( "dim="+ d);
			final ArrayList< Pair< Long, Long > > dimIntervals = new ArrayList<>();
	
			final long length = input.dimension( d );

			// can I use just 1 block?
			if ( length <= targetSize[ d ] )
			{
				final long min = input.min( d );
				final long max = input.max( d );

				dimIntervals.add( new ValuePair< Long, Long >( min, max ) );
				//System.out.println( "one block from " + min + " to " + max );
			}
			else
			{
				final long l = length;
				final long s = targetSize[ d ];
				final long o = overlapPx[ d ];

				// now we iterate the targetsize until we are as close as possible to an equal distribution (ideally 0.0 fraction)

				long lastImageSize = lastImageSize(l, s, o);// o + ( l - 2 * ( s-o ) - o ) % ( s - 2 * o + o );

				//System.out.println( "length: " + l );
				//System.out.println( "overlap: " + o );
				//System.out.println( "targetSize: " + s );
				//System.out.println( "lastImageSize: " + lastImageSize );

				final long finalSize;

				if ( optimize && lastImageSize != s )
				{
					long lastSize = s;
					long delta, currentLastImageSize;

					if ( lastImageSize <= s / 2 )
					{
						// increase image size until lastImageSize goes towards zero, then large
						//System.out.println( "small" );

						do
						{
							lastSize += minStepSize[ d ];
							currentLastImageSize = lastImageSize(l, lastSize, o);
							delta = lastImageSize - currentLastImageSize;

							lastImageSize = currentLastImageSize;
							//System.out.println( lastSize + ": " + lastImageSize + ", delta=" + delta );
						}
						while ( delta > 0 );

						finalSize = lastSize;
					}
					else
					{
						// decrease image size until lastImageSize is maximal 
						//System.out.println( "large" );

						do
						{
							lastSize -= minStepSize[ d ];
							currentLastImageSize = lastImageSize(l, lastSize, o);
							delta = lastImageSize - currentLastImageSize;

							lastImageSize = currentLastImageSize;
							//System.out.println( lastSize + ": " + lastImageSize + ", delta=" + delta );
						}
						while ( delta < 0 );

						finalSize = lastSize + minStepSize[ d ];
					}
				}
				else
				{
					finalSize = s;
				}

				//System.out.println( "finalSize: " + finalSize );
				//System.out.println( "finalLastImageSize: " + lastImageSize(l, finalSize, o) );

				dimIntervals.addAll( splitDim( input, d, finalSize, overlapPx[ d ] ) );
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

	public static long lastImageSize( final long l, final long s, final long o)
	{
		long size = o + ( l - 2 * ( s-o ) - o ) % ( s - 2 * o + o );

		// this happens when it is only two overlapping images
		if ( size < 0 )
			size = l + size;

		return size;
	}

	//public static double numCenterBlocks( final double l, final double s, final double o )
	//{
	//	return 	( l - 2.0 * ( s-o ) - o ) / ( s - 2.0 * o + o );
	//}

	public static ArrayList< Pair< Long, Long > > splitDim(
			final Interval input,
			final int d,
			final long s,
			final long o )
	{
		//System.out.println( "min=" + input.min( d ) + ", max=" + input.max( d ) );

		final ArrayList< Pair< Long, Long > > dimIntervals = new ArrayList<>();

		long from = input.min( d );
		long to;

		do
		{
			to = Math.min( input.max( d ), from + s - 1 );
			dimIntervals.add( new ValuePair<>( from, to ) );

			//System.out.println( "block " + (dimIntervals.size() - 1) + ": " + from + " " + to + " (size=" + (to-from+1) + ")" );

			//SimpleMultiThreading.threadWait( 100 );
			from = to - o + 1;
		}
		while ( to < input.max( d ) );

		return dimIntervals;
	}

	public static void main( String[] args )
	{
		Interval input = new FinalInterval( new long[]{ 0 }, new long[] { 14192 - 1 } );

		long[] overlapPx = new long[] { 128 };
		long[] targetSize = new long[] { 6000 };
		long[] minStepSize = new long[] { 64 };

		targetSize[ 0 ] = Split_Views.closestLongDivisableBy( targetSize[ 0 ], minStepSize[ 0 ] );
		overlapPx[ 0 ] = Split_Views.closestLargerLongDivisableBy( overlapPx[ 0 ], minStepSize[ 0 ] );

		boolean optimize = true;

		ArrayList< Interval > intervals = distributeIntervalsFixedOverlap( input, overlapPx, targetSize, minStepSize, optimize );

		System.out.println();

		for ( final Interval interval : intervals )
			System.out.println( Util.printInterval( interval ) );
	}
}
