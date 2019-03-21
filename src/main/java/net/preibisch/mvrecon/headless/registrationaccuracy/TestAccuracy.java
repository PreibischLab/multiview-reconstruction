package net.preibisch.mvrecon.headless.registrationaccuracy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import mpicbg.models.AffineModel3D;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.CorrespondingIP;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.NonRigidTools;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.SimpleReferenceIP;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.grid.ModelGrid;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.grid.NumericAffineModel3D;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOpt;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.strong.InterestPointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestAccuracy
{

	/*
	 * get nonRigid model grid, taken from first part of NonRigidTools.fuseVirtualInterpolatedNonRigid
	 */
	public static HashMap< ViewId, ModelGrid > getNonRigidModelGrid(
			final Map< ViewId, AffineTransform3D > viewRegistrations,
			final Map< ViewId, ViewInterestPointLists > viewInterestPoints,
			final Collection< ? extends ViewId > viewsToFuse,
			final Collection< ? extends ViewId > viewsToUse,
			final ArrayList< String > labels,
			final long[] controlPointDistance,
			final double alpha,
			final Interval boundingBox,
			final double downsampling,
			final ExecutorService service )
	{
		final Pair< Interval, AffineTransform3D > scaledBB = FusionTools.createDownsampledBoundingBox( boundingBox, downsampling );

		final Interval bbDS = scaledBB.getA();

		// finding the corresponding interest points is the same for all levels
		final HashMap< ViewId, ArrayList< CorrespondingIP > > annotatedIps = NonRigidTools.assembleIPsForNonRigid( viewInterestPoints, viewsToUse, labels );

		// find unique interest points in the pairs of images
		final ArrayList< HashSet< CorrespondingIP > > uniqueIPs = NonRigidTools.findUniqueInterestPoints( annotatedIps );

		// create final registrations for all views and a list of corresponding interest points
		final HashMap< ViewId, AffineTransform3D > downsampledRegistrations = NonRigidTools.createDownsampledRegistrations( viewsToUse, viewRegistrations, downsampling );

		// transform unique interest points
		final ArrayList< HashSet< CorrespondingIP > > transformedUniqueIPs = NonRigidTools.transformUniqueIPs( uniqueIPs, downsampledRegistrations );

		// compute an average location of each unique interest point that is defined by many (2...n) corresponding interest points
		// this location in world coordinates defines where each individual point should be "warped" to
		final HashMap< ViewId, ArrayList< SimpleReferenceIP > > uniquePoints = NonRigidTools.computeReferencePoints( annotatedIps.keySet(), transformedUniqueIPs );

		// compute all grids, if it does not contain a grid we use the old affine model
		final HashMap< ViewId, ModelGrid > nonrigidGrids = NonRigidTools.computeGrids( viewsToFuse, uniquePoints, controlPointDistance, alpha, bbDS, service );

		return nonrigidGrids;
	}


	public static void printCurrentNonrigidRegistrationAccuracy( final SpimData2 data, Set<Class<? extends Entity>> groupingFactors, String manualIpLabel, String automaticIpLabel)
	{

		// split the views into groups according to the grouping factors
		// also remove missing views
		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );

		for (final Group< ViewDescription > group : groups)
		{
			System.out.println( Group.gvids( group ) );
			final List< ViewDescription > vds = Group.getViewsSorted( group.getViews() );

			// calculate non-rigid model grid
			BoundingBoxMaximal bboxMaximal = new BoundingBoxMaximal( vds, data );
			BoundingBox bbox = bboxMaximal.estimate( "bbox" );
			final HashMap< ViewId, ModelGrid > modelGrids = getNonRigidModelGrid( 
					data.getViewRegistrations().getViewRegistrations().entrySet().stream().collect( Collectors.toMap( e -> e.getKey(), e -> e.getValue().getModel() ) ),
					data.getViewInterestPoints().getViewInterestPoints(),
					vds,
					vds,
					new ArrayList<>( Arrays.asList( new String[] {automaticIpLabel} ) ),
					new long[] {10l, 10l, 10l},
					1.0,
					bbox,
					1,
					Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() ) );

			for (AtomicInteger i = new AtomicInteger(); i.get()<vds.size(); i.incrementAndGet())
			{
				// error sum and correspondences count for view i
				final RealSum errSum = new RealSum();
				final AtomicInteger count = new AtomicInteger();
				for(AtomicInteger j = new AtomicInteger(); j.get() < vds.size(); j.incrementAndGet())
				{
					// error sum and correspondences count for view pair i<->j
					final RealSum errSumInner = new RealSum();
					final AtomicInteger countInnter = new AtomicInteger();

					// do not compare a view to itself
					if (i.get() == j.get())
						continue;

					// get IPs for the manual label
					final InterestPointList ipsA = data.getViewInterestPoints().getViewInterestPointLists( vds.get( i.get() ) ).getInterestPointList( manualIpLabel );
					final InterestPointList ipsB = data.getViewInterestPoints().getViewInterestPointLists( vds.get( j.get() ) ).getInterestPointList( manualIpLabel );

					// we do not have IPs for one of the views
					if (ipsA == null | ipsB == null)
						continue;

					// not sure if this check is necessary, but it wont hurt
					if (ipsA.getCorrespondingInterestPointsCopy() == null || ipsB.getCorrespondingInterestPointsCopy() == null)
						continue;
					
					// get all corresponding points in view i that have a correspondence in view j
					final List< CorrespondingInterestPoints > corrs =
							ipsA.getCorrespondingInterestPointsCopy().stream().filter( cp -> cp.getCorrespondingViewId().equals( vds.get( j.get() ) ) ).collect( Collectors.toList() );

					// keep a list of the points for access by index
					final List< InterestPoint > ipsACp = ipsA.getInterestPointsCopy();
					final List< InterestPoint > ipsBCp = ipsB.getInterestPointsCopy();

					corrs.forEach( corr -> {
						// get corresponding points in local pixel coordinates
						final InterestPoint ipA = ipsACp.get( corr.getDetectionId() );
						final InterestPoint ipB = ipsBCp.get( corr.getCorrespondingDetectionId() );
						double[] cA = ipA.getL().clone();
						double[] cB = ipB.getL().clone();

						// get view registrations
						final ViewRegistration vrA = data.getViewRegistrations().getViewRegistration( vds.get( i.get() ) );
						final ViewRegistration vrB = data.getViewRegistrations().getViewRegistration( vds.get( j.get() ) );
						final AffineTransform3D modelA = vrA.getModel();
						final AffineTransform3D modelB = vrB.getModel();

						// find the non-rigidly transformed points for the corresponding IPs 
						// 1) starting estimate: affine view registration
						modelA.apply( cA, cA );
						modelB.apply( cB, cB );
						// 2) find actual point via gradient descent
						double[] cNRa = findTransformedPoint( cA, ipA.getL().clone(), 0.1, 1000, 0.01, modelGrids.get( vds.get( i.get() ) ), 0.1 );
						double[] cNRb = findTransformedPoint( cB, ipB.getL().clone(), 0.1, 1000, 0.01, modelGrids.get( vds.get( j.get() ) ), 0.1 );

						// NB: this will fail with NullPointerException if we could not find the transformed point
						// we do not handle the exception, so that we will immediately know if something went wrong!
						final double distance = Util.distance( new RealPoint( cNRa ), new RealPoint( cNRb ) );

						// add to per-pair (errSumInner) and per-view (errSum) errors/counters
						errSumInner.add( distance );
						countInnter.incrementAndGet();
						errSum.add( distance );
						count.incrementAndGet();
					});
					// print mean per-pair error
					System.out.println( Group.pvid( vds.get( i.get() ) ) + "<=>" + Group.pvid( vds.get( j.get() ) )  + ", mean error : " + (countInnter.get() > 0 ? errSumInner.getSum() / countInnter.get() : " no correspondences ") );
				}
				// print mean per-view error
				System.out.println( Group.pvid( vds.get( i.get() ) ) + ", mean error : " + (count.get() > 0 ? errSum.getSum() / count.get() : " no correspondences ") );
			}
		}
	}

	public static void printCurrentRegistrationAccuracy( final SpimData2 data, Set<Class<? extends Entity>> groupingFactors, String manualIpLabel )
	{

		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );
		
		for (final Group< ViewDescription > group : groups)
		{
			System.out.println( Group.gvids( group ) );
			final List< ViewDescription > vds = Group.getViewsSorted( group.getViews() );
			for (AtomicInteger i = new AtomicInteger(); i.get()<vds.size(); i.incrementAndGet())
			{
				final RealSum errSum = new RealSum();
				final AtomicInteger count = new AtomicInteger();
				for(AtomicInteger j = new AtomicInteger(); j.get() < vds.size(); j.incrementAndGet())
				{
					final RealSum errSumInner = new RealSum();
					final AtomicInteger countInnter = new AtomicInteger();

					if (i.get() == j.get())
						continue;
					
					final InterestPointList ipsA = data.getViewInterestPoints().getViewInterestPointLists( vds.get( i.get() ) ).getInterestPointList( manualIpLabel );
					final InterestPointList ipsB = data.getViewInterestPoints().getViewInterestPointLists( vds.get( j.get() ) ).getInterestPointList( manualIpLabel );

					if (ipsA == null | ipsB == null)
						continue;

					// not sure if this check is necessary, but it wont hurt
					if (ipsA.getCorrespondingInterestPointsCopy() == null || ipsB.getCorrespondingInterestPointsCopy() == null)
						continue;
					
					final List< CorrespondingInterestPoints > corrs =
							ipsA.getCorrespondingInterestPointsCopy().stream().filter( cp -> cp.getCorrespondingViewId().equals( vds.get( j.get() ) ) ).collect( Collectors.toList() );
					
					final List< InterestPoint > ipsACp = ipsA.getInterestPointsCopy();
					final List< InterestPoint > ipsBCp = ipsB.getInterestPointsCopy();

					corrs.forEach( corr -> {
						final InterestPoint ipA = ipsACp.get( corr.getDetectionId() );
						final InterestPoint ipB = ipsBCp.get( corr.getCorrespondingDetectionId() );
						double[] cA = ipA.getL().clone();
						double[] cB = ipB.getL().clone();

						// apply view registration
						final ViewRegistration vrA = data.getViewRegistrations().getViewRegistration( vds.get( i.get() ) );
						final ViewRegistration vrB = data.getViewRegistrations().getViewRegistration( vds.get( j.get() ) );
						final AffineTransform3D modelA = vrA.getModel();
						final AffineTransform3D modelB = vrB.getModel();

						modelA.apply( cA, cA );
						modelB.apply( cB, cB );

						final double distance = Util.distance( new RealPoint( cA ), new RealPoint( cB ) );

						errSumInner.add( distance );
						countInnter.incrementAndGet();

						errSum.add( distance );
						count.incrementAndGet();
					});
					System.out.println( Group.pvid( vds.get( i.get() ) ) + "<=>" + Group.pvid( vds.get( j.get() ) )  + ", mean error : " + (countInnter.get() > 0 ? errSumInner.getSum() / countInnter.get() : " no correspondences ") );
				}
				
				System.out.println( Group.pvid( vds.get( i.get() ) ) + ", mean error : " + (count.get() > 0 ? errSum.getSum() / count.get() : " no correspondences ") );
			}
		}

		
	}

	public static void printManualIpRegistrationAccuracy(SpimData2 data, Set<Class<? extends Entity>> groupingFactors, String manualIpLabel)
	{

		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );
		
		for (final Group< ViewDescription > group : groups)
		{
			System.out.println( Group.gvids( group ) );
			// we do not care about the error, we assume the manual IPs to be as good as it gets
			final ConvergenceStrategy cs = new ConvergenceStrategy( Double.MAX_VALUE );
			//final TranslationModel3D model = new TranslationModel3D();
			final AffineModel3D model = new AffineModel3D();

			final List< ViewDescription > vds = Group.getViewsSorted( group.getViews() );
			final Set< ViewId > vidsUsed = new HashSet<>();
			final List<  Pair< Pair< ViewId, ViewId >, PairwiseResult< ? > > > pairs = new ArrayList<>();
			for (AtomicInteger i = new AtomicInteger(); i.get()<vds.size(); i.incrementAndGet())
				for(AtomicInteger j = new AtomicInteger( i.get()+1 ); j.get() < vds.size(); j.incrementAndGet())
				{
					final List< PointMatchGeneric< InterestPoint >> pmg = new ArrayList<>();
					final InterestPointList ipsA = data.getViewInterestPoints().getViewInterestPointLists( vds.get( i.get() ) ).getInterestPointList( manualIpLabel );
					final InterestPointList ipsB = data.getViewInterestPoints().getViewInterestPointLists( vds.get( j.get() ) ).getInterestPointList( manualIpLabel );

					if (ipsA == null | ipsB == null)
						continue;

					// not sure if this check is necessary, but it wont hurt
					if (ipsA.getCorrespondingInterestPointsCopy() == null || ipsB.getCorrespondingInterestPointsCopy() == null)
						continue;
					
					final List< CorrespondingInterestPoints > corrs =
							ipsA.getCorrespondingInterestPointsCopy().stream().filter( cp -> cp.getCorrespondingViewId().equals( vds.get( j.get() ) ) ).collect( Collectors.toList() );

					if (corrs.size() < model.getMinNumMatches())
						continue;

					vidsUsed.add( vds.get( i.get() ) );
					vidsUsed.add( vds.get( j.get() ) );
					final List< InterestPoint > ipsACp = ipsA.getInterestPointsCopy();
					final List< InterestPoint > ipsBCp = ipsB.getInterestPointsCopy();

					corrs.forEach( corr -> {
						final InterestPoint ipA = ipsACp.get( corr.getDetectionId() );
						final InterestPoint ipB = ipsBCp.get( corr.getCorrespondingDetectionId() );
						double[] cA = ipA.getL().clone();
						double[] cB = ipB.getL().clone();

						// apply calibration, rotation
						final ViewRegistration vrA = data.getViewRegistrations().getViewRegistration( vds.get( i.get() ) );
						final ViewRegistration vrB = data.getViewRegistrations().getViewRegistration( vds.get( j.get() ) );
						final ViewTransform calibA = vrA.getTransformList().get( vrA.getTransformList().size() -1 );
						final ViewTransform calibB = vrB.getTransformList().get( vrB.getTransformList().size() -1 );
						calibA.asAffine3D().apply( cA, cA );
						calibB.asAffine3D().apply( cB, cB );

						final AffineTransform3D flip = new AffineTransform3D();
						flip.rotate( 1, Math.PI );
						
						if (vds.get( i.get() ).getViewSetup().getAngle().getId() == 2)
							flip.apply( cA, cA );
						
						if (vds.get( j.get() ).getViewSetup().getAngle().getId() == 2)
							flip.apply( cB, cB );
						
						pmg.add( new PointMatchGeneric<>( new InterestPoint(ipA.getId(), cA), new InterestPoint(ipB.getId(), cB) ) );
					});

					PairwiseResult<InterestPoint> pwr = new PairwiseResult<>( false );
					pwr.setInliers( pmg, 0 );

					// add to Pairs
					pairs.add( new ValuePair<Pair< ViewId, ViewId >, PairwiseResult< ? > >( new ValuePair<>( vds.get( i.get() ), vds.get( j.get() ) ), pwr ) );
				}

			final InterestPointMatchCreator pmc = new InterestPointMatchCreator( pairs );
			// fix first view in group
			final Set<ViewId> fixed = new HashSet<>();
			fixed.add( vds.get( 0 ) );
			GlobalOpt.compute( model, pmc, cs, fixed, Group.toGroups( vidsUsed ) );
		}
	}

	/**
	 * find the target point a source point is mapped to via nonrigid transformations
	 * @param targetEstimate: initial guess for the target point e.g. via affine registration
	 * @param source: source point in image pixel coordinates
	 * @param rate: gradient descent step size
	 * @param maxIter: maximum number of GD iterations
	 * @param convergenceThresh: threshold of distance to real source for optimization to stop
	 * @param mgrid: non-rigid model grid (containing interpolated target->source transformations)
	 * @param gradientEstimationStep: delta for gradient estimation via central difference
	 * @return the non-rigidly transformed point corresponding to source, or null if optimization does not converge
	 */
	public static double[] findTransformedPoint(double[] targetEstimate, double[] source, double rate, int maxIter, double convergenceThresh, ModelGrid mgrid, double gradientEstimationStep )
	{
		// start from estimate, clone so we do not modify
		double[] x = targetEstimate.clone();

		final RealRandomAccess< NumericAffineModel3D > rra = mgrid.realRandomAccess();
		// keep source as RealPoint for easier distance calculation
		RealPoint sourcePoint = new RealPoint( source );

		for (int it = 0; it<maxIter; it++)
		{
			// temporary copy of x for this iteration
			final double[] xt = x.clone();

			// get current error
			rra.setPosition( x );
			final AffineModel3D mod = rra.get().getModel().copy();
			double[] s1 = mod.apply( x );
			double dist = Util.distance(sourcePoint , new RealPoint( s1 ) );
			System.out.println( "it: " + it + " dist: " + dist  );

			// we are within convergence threshold => done
			if (dist < convergenceThresh )
			{
				System.out.println( "found source point after " + it + " iterations." );
				return x;
			}

			// do GD update in each dimension
			for (int d = 0; d<mgrid.numDimensions(); d++)
			{

				// get central difference, using xt
				rra.move( gradientEstimationStep, d );
				xt[d] += gradientEstimationStep;
				final AffineModel3D modForward = rra.get().getModel().copy();
				double[] sourceForward = modForward.apply( xt );
				double dF = Util.distance(sourcePoint , new RealPoint( sourceForward ) );
				rra.move( -2.0 * gradientEstimationStep, d );
				xt[d] -= 2.0 * gradientEstimationStep;
				final AffineModel3D modBack = rra.get().getModel().copy();
				double[] sourceBack = modBack.apply( xt );
				double dB = Util.distance(sourcePoint , new RealPoint( sourceBack ) );

				// move xt, rra back to center
				xt[d] += gradientEstimationStep;
				rra.move( gradientEstimationStep, d );

				// do GD update on actual x
				double grad = (dF - dB) / (2.0 * gradientEstimationStep);
				x[d] -= grad * rate;
			}
		}

		// Gradient descent did not converge
		return null;
	}
	
	public static void main(String[] args)
	{

		/* --- DATA --- */
		SpimData2 spimData = null;
		try
		{
			 spimData = new XmlIoSpimData2( "" ).load( "/Volumes/davidh-ssd/BS_TEST/dataset_2_2_icp_all.xml" );
		}
		catch ( SpimDataException e ){ e.printStackTrace(); }

		/* --- PARAMETERS --- */
		// get results independently for angle/illumination combinations
		// or all together
		final Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
		groupingFactors.add( Illumination.class );
		groupingFactors.add( Angle.class );

		// label of the manual Interest Points
		final String manualIpLabel = "manual";
		// label of the IPs used for automatic registration
		// NB: correspondences must be saved correctly for Non-Rigid accuracy estimation
		final String automaticIpLabel = "beads";

		/* --- CALCULATE RESULTS --- */
		//printCurrentRegistrationAccuracy( spimData, groupingFactors, manualIpLabel );
		//printManualIpRegistrationAccuracy( spimData, groupingFactors, manualIpLabel );
		printCurrentNonrigidRegistrationAccuracy( spimData, groupingFactors, manualIpLabel, automaticIpLabel );
	}
}
