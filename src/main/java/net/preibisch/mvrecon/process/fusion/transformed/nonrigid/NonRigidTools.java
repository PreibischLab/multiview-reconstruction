package net.preibisch.mvrecon.process.fusion.transformed.nonrigid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import bdv.util.ConstantRandomAccessible;
import mpicbg.models.AffineModel3D;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxReorientation;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformView;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.TransformWeight;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.grid.ModelGrid;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval.CombineType;
import net.preibisch.mvrecon.process.fusion.transformed.weights.BlendingRealRandomAccessible;
import net.preibisch.mvrecon.process.fusion.transformed.weights.ContentBasedRealRandomAccessible;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class NonRigidTools
{
	public static RandomAccessibleInterval< FloatType > fuseVirtualInterpolatedNonRigid(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewsToFuse,
			final Collection< ? extends ViewId > viewsToUse,
			final ArrayList< String > labels,
			final boolean useBlending,
			final boolean useContentBased,
			final boolean displayDistances,
			final long[] controlPointDistance,
			final double alpha,
			final int interpolation,
			final Interval boundingBox1,
			final double downsampling,
			final ExecutorService service )
	{
		final Interval bb;

		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( boundingBox1, 1.0 / downsampling );
		else
			bb = boundingBox1;

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		// create final registrations for all views and a list of corresponding interest points
		final HashMap< ViewId, AffineTransform3D > registrations = new HashMap<>();

		for ( final ViewId viewId : viewsToUse )
		{
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();

			final AffineTransform3D model = vr.getModel().copy();

			if ( !Double.isNaN( downsampling ) )
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );

			registrations.put( viewId, model );
		}

		// new loop for interestpoints that need the registrations
		final HashMap< ViewId, ArrayList< CorrespondingIP > > annotatedIps = new HashMap<>();

		for ( final ViewId viewId : viewsToUse )
		{
			final ArrayList< CorrespondingIP > aips = new ArrayList<>();

			for ( final String label : labels )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading corresponding interest points for " + Group.pvid( viewId ) + ", label '" + label + "'" );
				
				if ( spimData.getViewInterestPoints().getViewInterestPointLists( viewId ).contains( label ) )
				{
					final InterestPointList ipList = spimData.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
					final Map< ViewId, ViewInterestPointLists > interestPointLists = spimData.getViewInterestPoints().getViewInterestPoints();

					final List< CorrespondingInterestPoints > cipList = ipList.getCorrespondingInterestPointsCopy();
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": There are " + cipList.size() + " corresponding interest points in total (to all views)." );

					final ArrayList< CorrespondingIP > aipsTmp = NonRigidTools.assembleAllCorrespondingPoints( viewId, ipList, cipList, viewsToUse, interestPointLists );

					if ( aipsTmp == null )
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED to assemble pairs of corresponding interest points for label " + label + " in view " + Group.pvid( viewId ) );
					else
						aips.addAll( aipsTmp );
				}
				else
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Label '" + label + "' does not exist in view " + Group.pvid( viewId ) );
				}
			}

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loaded " + aips.size() + " pairs of corresponding interest points." );

			if ( aips.size() < 12 )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": This number is not sufficient for non-rigid, using pre-computed affine." );
			}
			else
			{
				final double dist = NonRigidTools.transformAnnotatedIPs( aips, registrations );
				annotatedIps.put( viewId, aips );

				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Average distance = " + dist );
			}
		}

		// compute an average location of each unique interest point that is defined by many (2...n) corresponding interest points
		// this location in world coordinates defines where each individual point should be "warped" to
		final HashMap< ViewId, ArrayList< SimpleReferenceIP > > uniquePoints = NonRigidTools.computeReferencePoints( annotatedIps );

		// compute all grids, if it does not contain a grid we use the old affine model
		final HashMap< ViewId, ModelGrid > nonrigidGrids = NonRigidTools.computeGrids( viewsToFuse, uniquePoints, controlPointDistance, alpha, bb, service );

		// create virtual images
		for ( final ViewId viewId : viewsToFuse )
		{
			final ModelGrid grid = nonrigidGrids.get( viewId );
			final AffineTransform3D modelAffine = registrations.get( viewId ).copy(); // will be modified potentially
			final AffineModel3D invertedModelOpener;
			final RandomAccessibleInterval inputImg;

			if ( !displayDistances )
			{
				//
				// Display images (open smaller images if it makes sense)
				//

				// the model necessary to map to the image opened at a reduced resolution level
				final Pair< RandomAccessibleInterval, AffineTransform3D > input =
						DownsampleTools.openDownsampled2( spimData.getSequenceDescription().getImgLoader(), viewId, modelAffine, null );
	
				// concatenate the downsampling transformation model to the affine transform
				if ( input.getB() != null )
				{
					modelAffine.concatenate( input.getB() );
					invertedModelOpener = TransformationTools.getModel( input.getB() ).createInverse();
				}
				else
				{
					invertedModelOpener = null;
				}
	
				inputImg = input.getA();

				if ( grid == null )
					images.add( TransformView.transformView( inputImg, modelAffine, bb, 0, interpolation ) );
				else
					images.add( NonRigidTools.transformViewNonRigidInterpolated( inputImg, grid, invertedModelOpener, bb, 0, interpolation ) );
			}
			else
			{
				//
				// Display distances (open at full resolution)
				//

				invertedModelOpener = null;
				inputImg = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

				if ( grid == null )
					images.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 0 ), 3 ), new FinalInterval( bb ) ) );
				else
					images.add( NonRigidTools.visualizeDistancesViewNonRigidInterpolated( inputImg, grid, modelAffine, bb, 0, interpolation ) );
			}

			//
			// weights
			//
			if ( useBlending || useContentBased )
			{
				RandomAccessibleInterval< FloatType > transformedBlending = null, transformedContentBased = null;

				// instantiate blending if necessary
				if ( useBlending )
				{
					final float[] blending = Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
					final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

					// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
					FusionTools.adjustBlending( spimData.getSequenceDescription().getViewDescription( viewId ), blending, border, modelAffine );

					if ( grid == null )
						transformedBlending = TransformWeight.transformBlending( inputImg, border, blending, modelAffine, bb );
					else
						transformedBlending = NonRigidWeightTools.transformWeightNonRigidInterpolated(
								new BlendingRealRandomAccessible(
										new FinalInterval( inputImg ), border, blending ),
										grid,
										invertedModelOpener,
										bb );
				}

				// instantiate content based if necessary
				if ( useContentBased )
				{
					final double[] sigma1 = Util.getArrayFromValue( FusionTools.defaultContentBasedSigma1, 3 );
					final double[] sigma2 = Util.getArrayFromValue( FusionTools.defaultContentBasedSigma2, 3 );

					// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
					FusionTools.adjustContentBased( spimData.getSequenceDescription().getViewDescription( viewId ), sigma1, sigma2, modelAffine );

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Estimating Entropy for " + Group.pvid( viewId ) );

					if ( grid == null )
						transformedContentBased = TransformWeight.transformContentBased( inputImg, new CellImgFactory<>( new ComplexFloatType() ), sigma1, sigma2, modelAffine, bb );
					else
						transformedContentBased = 
								NonRigidWeightTools.transformWeightNonRigidInterpolated(
									new ContentBasedRealRandomAccessible(
											inputImg,
											new CellImgFactory<>( new ComplexFloatType() ),
											sigma1,
											sigma2 ),
									grid,
									invertedModelOpener,
									bb );
				}

				if ( useContentBased && useBlending )
				{
					weights.add( new CombineWeightsRandomAccessibleInterval(
									new FinalInterval( transformedBlending ),
									transformedBlending,
									transformedContentBased,
									CombineType.MUL ) );
				}
				else if ( useBlending )
				{
					weights.add( transformedBlending );
				}
				else if ( useContentBased )
				{
					weights.add( transformedContentBased );
				}
			}
			else
			{
				final RandomAccessibleInterval< FloatType > imageArea =
						Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( inputImg ) );

				if ( grid == null )
					weights.add( TransformView.transformView( imageArea, modelAffine, bb, 0, 0 ) );
				else
					weights.add( NonRigidTools.transformViewNonRigidInterpolated( imageArea, grid, invertedModelOpener, bb, 0, 0 ) );
			}
		}

		return new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformViewNonRigidInterpolated(
			final RandomAccessibleInterval< T > input,
			final ModelGrid grid,
			final AffineModel3D invertedModelOpener,
			final Interval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final long[] size = new long[ input.numDimensions() ];

		for ( int d = 0; d < size.length; ++d )
			size[ d ] = boundingBox.dimension( d );

		final InterpolatingNonRigidRandomAccessible< T > virtual = new InterpolatingNonRigidRandomAccessible< T >( input, grid, invertedModelOpener, false, 0.0f, new FloatType( outsideValue ), boundingBox );

		if ( interpolation == 0 )
			virtual.setNearestNeighborInterpolation();
		else
			virtual.setLinearInterpolation();

		return Views.interval( virtual, new FinalInterval( size ) );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformViewNonRigid(
			final RandomAccessibleInterval< T > input,
			final Collection< ? extends NonrigidIP > ips,
			final double alpha,
			final AffineModel3D invertedModelOpener,
			final Interval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final long[] size = new long[ input.numDimensions() ];

		for ( int d = 0; d < size.length; ++d )
			size[ d ] = boundingBox.dimension( d );

		final NonRigidRandomAccessible< T > virtual = new NonRigidRandomAccessible< T >( input, ips, alpha, invertedModelOpener, false, 0.0f, new FloatType( outsideValue ), boundingBox );

		if ( interpolation == 0 )
			virtual.setNearestNeighborInterpolation();
		else
			virtual.setLinearInterpolation();

		return Views.interval( virtual, new FinalInterval( size ) );
	}

	public static RandomAccessibleInterval< FloatType > visualizeDistancesViewNonRigidInterpolated(
			final Interval input,
			final ModelGrid grid,
			final AffineTransform3D originalTransform,
			final Interval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final long[] size = new long[ input.numDimensions() ];

		for ( int d = 0; d < size.length; ++d )
			size[ d ] = boundingBox.dimension( d );

		final DistanceVisualizingRandomAccessible virtual = new DistanceVisualizingRandomAccessible( input, grid, originalTransform, new FloatType( outsideValue ), boundingBox );

		if ( interpolation == 0 )
			virtual.setNearestNeighborInterpolation();
		else
			virtual.setLinearInterpolation();

		return Views.interval( virtual, new FinalInterval( size ) );
	}

	public static HashMap< ViewId, ModelGrid > computeGrids(
			final Collection< ? extends ViewId > viewsToFuse,
			final HashMap< ? extends ViewId, ? extends Collection< ? extends NonrigidIP > > uniquePoints,
			final long[] controlPointDistance,
			final double alpha,
			final Interval boundingBox,
			final ExecutorService service )
	{
		final ArrayList< Callable< Pair< ViewId, ModelGrid > > > tasks = new ArrayList<>();

		for ( final ViewId viewId : viewsToFuse )
		{
			tasks.add( new Callable< Pair< ViewId, ModelGrid > >()
			{
				@Override
				public Pair< ViewId, ModelGrid > call() throws Exception
				{
					final Collection< ? extends NonrigidIP > ips = uniquePoints.get( viewId );

					if ( ips == null )
					{
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": NO POINTS to interpolate non-rigid model for " + Group.pvid( viewId ) + " - using affine model" );
						return new ValuePair< ViewId, ModelGrid >( null, null );
					}

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Interpolating non-rigid model (a=" + alpha + ") for " + Group.pvid( viewId ) + " using " + ips.size() + " points and stepsize " + Util.printCoordinates( controlPointDistance ) + " Interval: " + Util.printInterval( boundingBox ) );

					try
					{
						final ModelGrid grid = new ModelGrid( controlPointDistance, boundingBox, ips, alpha );
						return new ValuePair< ViewId, ModelGrid >( viewId, grid );
					}
					catch ( Exception e )
					{
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED to interpolate non-rigid model for " + Group.pvid( viewId ) + ": " + e + " - using affine model" );
						e.printStackTrace();
						return new ValuePair< ViewId, ModelGrid >( null, null );
					}
				}
			});
		}

		final HashMap< ViewId, ModelGrid > nonrigidGrids = new HashMap<>();

		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< Pair< ViewId, ModelGrid > > > futures = service.invokeAll( tasks );

			for ( final Future< Pair< ViewId, ModelGrid > > f : futures )
			{
				final Pair< ViewId, ModelGrid > p = f.get();

				if ( p.getA() != null && p.getB() != null )
					nonrigidGrids.put( p.getA(), p.getB() );
			}

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": In total " + nonrigidGrids.keySet().size() + "/" + viewsToFuse.size() + " views are fused non-rigidly," );

			return nonrigidGrids;
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute non-rigid grids: " + e );
			e.printStackTrace();
			return null;
		}
	}

	public static HashMap< ViewId, ArrayList< SimpleReferenceIP > > computeReferencePoints( final HashMap< ViewId, ArrayList< CorrespondingIP > > annotatedIps )
	{
		final ArrayList< CorrespondingIP > aips = new ArrayList<>();

		for ( final ArrayList< CorrespondingIP > aipl : annotatedIps.values() )
			aips.addAll( aipl );

		// find unique interest points in the pairs of images
		final ArrayList< HashSet< CorrespondingIP > > uniqueIPs = findUniqueInterestPoints( aips );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Total number of pairs are " + aips.size() + ", which are " + uniqueIPs.size() + " unique interest points." );

		// some statistics
		final int[] count = uniqueInterestPointCounts( uniqueIPs );

		System.out.println( "Structure: " );
		for ( int i = 0; i < count.length; ++i )
			if ( count[ i ] > 0 )
				System.out.println( i + ": " + count[ i ] );

		final RealSum sum = new RealSum();
		int countDist = 0;
		double maxDist = 0;

		// compute the centers
		for ( final HashSet< CorrespondingIP > uniqueIP : uniqueIPs )
		{
			final double[] avgPosW = new double[ 3 ];

			for ( final CorrespondingIP aip : uniqueIP )
			{
				for ( int d = 0; d < avgPosW.length; ++d )
					avgPosW[ d ] += aip.w[ d ];
			}

			for ( int d = 0; d < avgPosW.length; ++d )
				avgPosW[ d ] /= (double)uniqueIP.size();
	
			for ( final CorrespondingIP aip : uniqueIP )
			{
				aip.setTargetW( avgPosW );

				final double dist = Math.sqrt( BoundingBoxReorientation.squareDistance( aip.w[ 0 ], aip.w[ 1 ], aip.w[ 2 ], aip.targetW[ 0 ], aip.targetW[ 1 ], aip.targetW[ 2 ] ) );
				sum.add( dist );
				maxDist = Math.max( maxDist, dist );
				++countDist;
			}
		}

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Average distance from unique interest point: " + ( sum.getSum() / (double)countDist) );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Max distance from unique interest point: " + maxDist );

		final ArrayList< ViewId > viewIds = new ArrayList<>( annotatedIps.keySet() );
		Collections.sort( viewIds );

		//final ArrayList< HashSet< CorrespondingIP > > uniqueIPs = findUniqueInterestPoints( aips );

		final HashMap< ViewId, ArrayList< SimpleReferenceIP > > uniquePointsPerView = new HashMap<>();

		//
		// go over all groups, find the points that belong to this view, make one point each
		//
		for ( final ViewId viewId : viewIds )
		{
			final ArrayList< SimpleReferenceIP > myIPs = new ArrayList<>();

			for ( final HashSet< CorrespondingIP > uniqueIP : uniqueIPs )
			{
				CorrespondingIP myIp = null;

				for ( final CorrespondingIP ip : uniqueIP )
				{
					if ( ip.getViewId().equals( viewId ) )
					{
						myIp = ip;
						break; // TODO: there could be more than one if there are inconsistencies ...
					}
				}

				if ( myIp != null )
					myIPs.add( new SimpleReferenceIP( myIp.getL(), myIp.getW(), myIp.getTargetW() ) );
			}

			uniquePointsPerView.put( viewId, myIPs );
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Unique interest points for " + Group.pvid( viewId ) + ": " + myIPs.size() );
		}

		return uniquePointsPerView;
	}

	public static ArrayList< HashSet< CorrespondingIP > > findUniqueInterestPoints( final Collection< CorrespondingIP > pairs)
	{
		final ArrayList< HashSet< CorrespondingIP > > groups = new ArrayList<>();
		final HashSet< CorrespondingIP > unassignedCorr = new HashSet<>( pairs );

		while ( unassignedCorr.size() > 0 )
		{
			final HashSet< CorrespondingIP > group = new HashSet<CorrespondingIP>();
			final CorrespondingIP start = unassignedCorr.iterator().next();
			group.add( start );
			unassignedCorr.remove( start );

			final LinkedList< CorrespondingIP > potentialCorrespondences = new LinkedList<>();
			potentialCorrespondences.add( start );

			final HashSet< CorrespondingIP > visited = new HashSet<>();

			while ( potentialCorrespondences.size() > 0 )
			{
				final CorrespondingIP pc = potentialCorrespondences.remove();
				visited.add( pc );

				final ArrayList< CorrespondingIP > toRemove = new ArrayList<>();

				for ( final CorrespondingIP newCorr : unassignedCorr )
				{
					if ( pc.ip.equals( newCorr.ip ) || pc.ip.equals( newCorr.corrIp ) || pc.corrIp.equals( newCorr.ip ) || pc.corrIp.equals( newCorr.corrIp ))
					{
						group.add( newCorr );
						toRemove.add( newCorr );
						potentialCorrespondences.add( newCorr );
					}
				}

				unassignedCorr.removeAll( toRemove );
			}

			groups.add( group );

		}
		return groups;
	}

	public static double transformAnnotatedIPs( final Collection< CorrespondingIP > aips, final Map< ViewId, AffineTransform3D > models )
	{
		final RealSum sum = new RealSum( aips.size() );

		for ( final CorrespondingIP aip : aips )
		{
			aip.transform( models.get( aip.viewId ), models.get( aip.corrViewId ) );

			sum.add( Math.sqrt( BoundingBoxReorientation.squareDistance( aip.w[ 0 ], aip.w[ 1 ], aip.w[ 2 ], aip.corrW[ 0 ], aip.corrW[ 1 ], aip.corrW[ 2 ] ) ) );
			//RealPoint.wrap( aip.w );
		}

		return sum.getSum() / (double)aips.size();
	}

	public static ArrayList< CorrespondingIP > assembleAllCorrespondingPoints(
			final ViewId viewId,
			final InterestPointList ipList,
			final List< ? extends CorrespondingInterestPoints > cipList,
			final Collection< ? extends ViewId > viewsToUse,
			final Map< ? extends ViewId, ? extends ViewInterestPointLists > interestPointLists )
	{
		// result
		final ArrayList< CorrespondingIP > ipPairs = new ArrayList<>();

		// sort all ViewIds into a set
		final HashSet< ViewId > views = new HashSet<>( viewsToUse );

		// sort all interest points into a HashMap
		final HashMap< Integer, InterestPoint > ips = new HashMap<>();

		for ( final InterestPoint ip : ipList.getInterestPointsCopy() )
			ips.put( ip.getId(), ip );

		// sort all corresponding interest points into a HashMap
		final HashMap< ViewId, List< IPL > > loadedIps = new HashMap<>();

		for ( final CorrespondingInterestPoints cip : cipList )
		{
			// local interest point
			final InterestPoint ip = ips.get( cip.getDetectionId() );

			if ( ip == null )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Instance for id=" + ip + " of local interest point does not exist. Stopping." );
				return null;
			}

			// get corresponding interest point instance without reloading all the time
			final ViewId corrViewId = cip.getCorrespondingViewId();
			final String corrLabel = cip.getCorrespodingLabel();

			// only processing those views that are requested
			if ( !views.contains( corrViewId ) )
				continue;

			final List< IPL > ipls;

			// were there ever any interest points for this corresponding ViewId loaded?
			if ( loadedIps.containsKey( corrViewId )  )
			{
				ipls = loadedIps.get( corrViewId );
			}
			else
			{
				ipls = new ArrayList<>();
				loadedIps.put( corrViewId, ipls );
			}

			// were the interest points for this corresponding label of this corresponding ViewId loaded?
			IPL ipl = getIPL( ipls, corrLabel );

			if ( ipl == null )
			{
				// load corresponding interest points
				final ViewInterestPointLists vipl = interestPointLists.get( corrViewId );

				if ( vipl == null )
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": No interestpoints for " +  Group.pvid( corrViewId ) + " exist. Stopping." );
					return null;
				}

				final InterestPointList corrIpList = vipl.getInterestPointList( corrLabel );

				if ( corrIpList == null )
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Corresponding Label '" + corrLabel + "' does not exist for " +  Group.pvid( corrViewId ) + ". Stopping." );
					return null;
				}

				final HashMap< Integer, InterestPoint > corrIps = new HashMap<>();

				// sort all corresponding interest points into a HashMap
				for ( final InterestPoint corrIp : corrIpList.getInterestPointsCopy() )
					corrIps.put( corrIp.getId(), corrIp );

				ipl = new IPL( corrLabel, corrIps );

				ipls.add( ipl );
			}

			final int corrId = cip.getCorrespondingDetectionId();
			final InterestPoint corrIp = ipl.map.get( corrId );

			if ( corrIp == null )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Instance for id=" + corrId + " of corresponding Label '" + corrLabel + "' does not exist for " +  Group.pvid( corrViewId ) + ". Stopping." );
				return null;
			}
	
			ipPairs.add( new CorrespondingIP( ip, viewId, corrIp, corrViewId ) );
		}

		return ipPairs;
	}

	public static int[] uniqueInterestPointCounts( final List< HashSet< CorrespondingIP > > groups )
	{
		if ( groups == null || groups.size() == 0 )
			return new int[ 1 ];

		int maxSize = 0;

		for ( final HashSet< ? > group : groups )
			maxSize = Math.max( group.size(), maxSize );

		final int[] counts = new int[ maxSize + 1 ];

		for ( final HashSet< ? > group : groups )
			++counts[ group.size() ];

		return counts;
	}

	protected static IPL getIPL( final Collection< IPL > ipls, final String label )
	{
		for ( final IPL ipl : ipls )
			if ( ipl.label.equals( label ) )
				return ipl;

		return null;
	}

}
