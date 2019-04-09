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

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
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
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.ViewSetupUtils;
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


	public static void printCurrentNonrigidRegistrationAccuracy( final SpimData2 data, Set<Class<? extends Entity>> groupingFactors, String manualIpLabel, String automaticIpLabel, boolean applyCalibration )
	{
		printCurrentNonrigidRegistrationAccuracy( data, groupingFactors, manualIpLabel, automaticIpLabel, applyCalibration, false, 1 );
	}

	public static void printCurrentNonrigidRegistrationAccuracy( final SpimData2 data, Set<Class<? extends Entity>> groupingFactors, String manualIpLabel, String automaticIpLabel, boolean applyCalibration, boolean getPreRegistrationFromManualPoints, int foldSplit)
	{

		// split the views into groups according to the grouping factors
		// also remove missing views
		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );

		final HashMap<ViewId, Double> perViewErrors = new HashMap<>();

		for (final Group< ViewDescription > group : groups)
		{
			System.out.println( Group.gvids( group ) );
			final List< ViewDescription > vds = Group.getViewsSorted( group.getViews() );

			// get current view registrations
			final HashMap< ViewId, AffineTransform3D > viewRegistrations = new HashMap<>( data.getViewRegistrations().getViewRegistrations().entrySet().stream().collect( Collectors.toMap( e -> e.getKey(), e -> e.getValue().getModel() ) ) );

			// estimate new registration from manual interest points
			// i.e. calculate new affine view registrations from them
			if (getPreRegistrationFromManualPoints)
			{
				// first view transform for calibration
				ViewRegistration vrFirst = data.getViewRegistrations().getViewRegistration( vds.get( 0 ) );
				vrFirst.updateModel();

				// flip transform
				final AffineTransform3D flip = new AffineTransform3D();
				flip.rotate( 1, Math.PI );

				// get manual ip registration with calibration / flip
				HashMap< ViewId, Tile< AffineModel3D > > manualVRmpi = printManualIpRegistrationAccuracy( data, groupingFactors, new AffineModel3D(), manualIpLabel, true, foldSplit, false );
				viewRegistrations.clear();
				viewRegistrations.putAll( new HashMap<>( manualVRmpi.entrySet().stream().collect( Collectors.toMap(
						e -> e.getKey(),
						e -> {
							AffineModel3D m = e.getValue().getModel();
							AffineTransform3D res = new AffineTransform3D();
							res.set( m.getMatrix( null ) );

							// Angle 2 -> model has to include flip
							if (data.getSequenceDescription().getViewDescriptions().get(e.getKey()).getViewSetup().getAngle().getId() == 2)
								res.concatenate( flip );

							// find the calibration transform
							AffineGet calib = null;
							for (int i = 0; i<vrFirst.getTransformList().size(); i++)
								if (vrFirst.getTransformList().get( i ).getName().equals( "calibration" ))
									calib = vrFirst.getTransformList().get( i ).asAffine3D();

							// model has to include calibration
							res.concatenate( calib );
							return res;
						}
				) ) ) );
			}

			// calclulate bounding box with (new) registrations
			BoundingBoxMaximal bboxMaximal = new BoundingBoxMaximal( vds,
					new HashMap<>(vds.stream().collect( Collectors.toMap( e -> e,
							e -> ViewSetupUtils.getSizeOrLoad( e.getViewSetup(), e.getTimePoint(), data.getSequenceDescription().getImgLoader() ) ) ) ),
					viewRegistrations );
			BoundingBox bbox = bboxMaximal.estimate( "bbox" );

			// calculate non-rigid model grid
			final HashMap< ViewId, ModelGrid > modelGrids = getNonRigidModelGrid( 
					viewRegistrations,
					data.getViewInterestPoints().getViewInterestPoints(),
					vds,
					vds,
					new ArrayList<>( Arrays.asList( getPreRegistrationFromManualPoints ? new String[] {manualIpLabel} : new String[] {automaticIpLabel}) ),
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

					// do not compare a view to itself or others in the same split
					if (vds.get( i.get() ).getViewSetupId()/foldSplit == vds.get( j.get() ).getViewSetupId()/foldSplit)
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

						final AffineTransform3D modelA = viewRegistrations.get( vds.get( i.get() ) );
						final AffineTransform3D modelB = viewRegistrations.get( vds.get( j.get() ) );

						// find the non-rigidly transformed points for the corresponding IPs 
						// 1) starting estimate: affine view registration
						modelA.apply( cA, cA );
						modelB.apply( cB, cB );
						// 2) find actual point via gradient descent
						double[] cNRa = findTransformedPoint( cA, ipA.getL().clone(), 0.1, 1000, 0.01, modelGrids.get( vds.get( i.get() ) ), 0.1 );
						double[] cNRb = findTransformedPoint( cB, ipB.getL().clone(), 0.1, 1000, 0.01, modelGrids.get( vds.get( j.get() ) ), 0.1 );

						// print big warning in case we could not find a point
						if (cNRa == null | cNRb == null)
						{
							System.err.println( "WARNING: target point could not be found" );
							return;
						}

						// un-do calibration if necessary
						if (!applyCalibration)
						{

							ViewTransform calibA = null;
							for (ViewTransform v: vrA.getTransformList())
								if (v.getName().equals( "calibration" ))
									calibA = v;

							ViewTransform calibB = null;
							for (ViewTransform v: vrB.getTransformList())
								if (v.getName().equals( "calibration" ))
									calibB = v;

							calibA.asAffine3D().applyInverse( cNRa, cNRa );
							calibB.asAffine3D().applyInverse( cNRb, cNRb );
						}

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

				if (count.get() > 0)
					perViewErrors.put( vds.get( i.get() ), errSum.getSum() / count.get() );
			}
		}

		System.out.println( "=== RESULTS ===" );
		perViewErrors.entrySet().forEach( e -> {
			System.out.println( " = Error View: " + Group.pvid( e.getKey() ) + ": " + e.getValue() );
		});
		System.out.println( "=== Mean Error: " + perViewErrors.values().stream().reduce( 0.0, (a,b) -> a+b ) / perViewErrors.size() );
		
	}

	public static void printCurrentRegistrationAccuracy( final SpimData2 data, Set<Class<? extends Entity>> groupingFactors, String manualIpLabel, boolean applyCalibration, int foldSplit)
	{

		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );

		final HashMap<ViewId, Double> perViewErrors = new HashMap<>();

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
					if (vds.get( i.get() ).getViewSetupId()/foldSplit == vds.get( j.get() ).getViewSetupId()/foldSplit)
						continue;

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

						if (!applyCalibration)
						{
							ViewTransform calibA = null;
							for (ViewTransform v: vrA.getTransformList())
								if (v.getName().equals( "calibration" ))
									calibA = v;

							ViewTransform calibB = null;
							for (ViewTransform v: vrB.getTransformList())
								if (v.getName().equals( "calibration" ))
									calibB = v;

							calibA.asAffine3D().applyInverse( cA, cA );
							calibB.asAffine3D().applyInverse( cB, cB );
						}

						final double distance = Util.distance( new RealPoint( cA ), new RealPoint( cB ) );

						errSumInner.add( distance );
						countInnter.incrementAndGet();

						errSum.add( distance );
						count.incrementAndGet();
					});
					System.out.println( Group.pvid( vds.get( i.get() ) ) + "<=>" + Group.pvid( vds.get( j.get() ) )  + ", mean error : " + (countInnter.get() > 0 ? errSumInner.getSum() / countInnter.get() : " no correspondences ") );
				}
				
				System.out.println( Group.pvid( vds.get( i.get() ) ) + ", mean error : " + (count.get() > 0 ? errSum.getSum() / count.get() : " no correspondences ") );
				if (count.get() > 0)
					perViewErrors.put( vds.get( i.get() ), errSum.getSum() / count.get() );
			}
		}

		System.out.println( "=== RESULTS ===" );
		perViewErrors.entrySet().forEach( e -> {
			System.out.println( " = Error View: " + Group.pvid( e.getKey() ) + ": " + e.getValue() );
		});
		System.out.println( "=== Mean Error: " + perViewErrors.values().stream().reduce( 0.0, (a,b) -> a+b ) / perViewErrors.size() );

		
	}

	public static <M extends AbstractAffineModel3D< M >> HashMap< ViewId, Tile< M > > printManualIpRegistrationAccuracy(SpimData2 data, Set<Class<? extends Entity>> groupingFactors, M model, String manualIpLabel, boolean applyCalibration, int foldSplit, boolean printBetweenTiles)
	{

		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );

		HashMap< ViewId, Tile< M > > combinedResult = new HashMap<>();
		final HashMap<ViewId, Double> perViewErrors = new HashMap<>();

		for (final Group< ViewDescription > group : groups)
		{
			System.out.println( Group.gvids( group ) );
			// we do not care about the error, we assume the manual IPs to be as good as it gets
			final ConvergenceStrategy cs = new ConvergenceStrategy( Double.MAX_VALUE );

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
						if (true)
						{
							final ViewRegistration vrA = data.getViewRegistrations().getViewRegistration( vds.get( i.get() ) );
							final ViewRegistration vrB = data.getViewRegistrations().getViewRegistration( vds.get( j.get() ) );

							ViewTransform calibA = null;
							for (ViewTransform v: vrA.getTransformList())
								if (v.getName().equals( "calibration" ))
									calibA = v;

							ViewTransform calibB = null;
							for (ViewTransform v: vrB.getTransformList())
								if (v.getName().equals( "calibration" ))
									calibB = v;

							calibA.asAffine3D().apply( cA, cA );
							calibB.asAffine3D().apply( cB, cB );
						}

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
			final HashMap< ViewId, Tile< M > > globalOptResult = GlobalOpt.compute( model, pmc, cs, fixed, Group.toGroups( vidsUsed ) );

			combinedResult.putAll( globalOptResult );

			if (printBetweenTiles)
			{
				for (AtomicInteger i = new AtomicInteger(); i.get()<vds.size(); i.incrementAndGet())
				{
					final RealSum errSum = new RealSum();
					final AtomicInteger count = new AtomicInteger();
					for(AtomicInteger j = new AtomicInteger(); j.get() < vds.size(); j.incrementAndGet())
					{
						if (vds.get( i.get() ).getViewSetupId()/foldSplit == vds.get( j.get() ).getViewSetupId()/foldSplit)
							continue;
	
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
							final AffineTransform3D modelA = new AffineTransform3D();
							modelA.set( globalOptResult.get( vds.get( i.get() ) ).getModel().getMatrix( null ) );
							final AffineTransform3D modelB = new AffineTransform3D();
							modelB.set( globalOptResult.get( vds.get( j.get() ) ).getModel().getMatrix( null ) );
	
							if (true)
							{
								ViewTransform calibA = null;
								for (ViewTransform v: vrA.getTransformList())
									if (v.getName().equals( "calibration" ))
										calibA = v;
	
								ViewTransform calibB = null;
								for (ViewTransform v: vrB.getTransformList())
									if (v.getName().equals( "calibration" ))
										calibB = v;
	
								calibA.asAffine3D().apply( cA, cA );
								calibB.asAffine3D().apply( cB, cB );
								
								final AffineTransform3D flip = new AffineTransform3D();
								flip.rotate( 1, Math.PI );
								
								if (vds.get( i.get() ).getViewSetup().getAngle().getId() == 2)
									flip.apply( cA, cA );
								
								if (vds.get( j.get() ).getViewSetup().getAngle().getId() == 2)
									flip.apply( cB, cB );
							}
							
							modelA.apply( cA, cA );
							modelB.apply( cB, cB );
	
							if (!applyCalibration)
							{
								ViewTransform calibA = null;
								for (ViewTransform v: vrA.getTransformList())
									if (v.getName().equals( "calibration" ))
										calibA = v;
	
								ViewTransform calibB = null;
								for (ViewTransform v: vrB.getTransformList())
									if (v.getName().equals( "calibration" ))
										calibB = v;
	
								calibA.asAffine3D().applyInverse( cA, cA );
								calibB.asAffine3D().applyInverse( cB, cB );
							}
	
							final double distance = Util.distance( new RealPoint( cA ), new RealPoint( cB ) );
	
							errSumInner.add( distance );
							countInnter.incrementAndGet();
	
							errSum.add( distance );
							count.incrementAndGet();
						});
						System.out.println( Group.pvid( vds.get( i.get() ) ) + "<=>" + Group.pvid( vds.get( j.get() ) )  + ", mean error : " + (countInnter.get() > 0 ? errSumInner.getSum() / countInnter.get() : " no correspondences ") );
					}

					System.out.println( Group.pvid( vds.get( i.get() ) ) + ", mean error : " + (count.get() > 0 ? errSum.getSum() / count.get() : " no correspondences ") );
					if (count.get() > 0)
						perViewErrors.put( vds.get( i.get() ), errSum.getSum() / count.get() );
				}
			}
			
		}
		
		if (printBetweenTiles)
		{
			System.out.println( "=== RESULTS ===" );
			perViewErrors.entrySet().forEach( e -> {
				System.out.println( " = Error View: " + Group.pvid( e.getKey() ) + ": " + e.getValue() );
			});
			System.out.println( "=== Mean Error: " + perViewErrors.values().stream().reduce( 0.0, (a,b) -> a+b ) / perViewErrors.size() );
		}

		return combinedResult;
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
			 spimData = new XmlIoSpimData2( "" ).load("/Volumes/SamsungOld/Fabio Testdata/_Benchmark/dataset_2_0_icp_angle_illum_1.split.xml"); 
					 //"/Volumes/davidh-ssd/BS_TEST/dataset_2_2_icp_all_1.split.xml" );
		}
		catch ( SpimDataException e ){ e.printStackTrace(); }

		/* --- PARAMETERS --- */
		// get results independently for angle/illumination combinations
		// or all together
		final Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
		groupingFactors.add( Illumination.class );
		groupingFactors.add( Angle.class );

		// nothing: all
		// angle: angle
		// both: angle_illum

		// label of the manual Interest Points
		final String manualIpLabel = "manual";
		// label of the IPs used for automatic registration
		// NB: correspondences must be saved correctly for Non-Rigid accuracy estimation
		final String automaticIpLabel = "stuff2";

		// whether to get errors in calibrated, isotropic units or raw pixel units
		boolean applyCalibration = false;

		/* --- CALCULATE RESULTS --- */
		printCurrentRegistrationAccuracy( spimData, groupingFactors, manualIpLabel, applyCalibration, 8 ); //actual errors
		//printManualIpRegistrationAccuracy( spimData, groupingFactors, new AffineModel3D(), manualIpLabel, applyCalibration, 8, true ); //theoretical results
		//printCurrentNonrigidRegistrationAccuracy( spimData, groupingFactors, manualIpLabel, automaticIpLabel, applyCalibration, false, 8 ); //non-rigid, true for theoretical

		/*
		//printCurrentRegistrationAccuracy( spimData, groupingFactors, manualIpLabel, applyCalibration, 8 );
		printManualIpRegistrationAccuracy( spimData, groupingFactors, new AffineModel3D(), manualIpLabel, applyCalibration, 8, true );
		//printCurrentNonrigidRegistrationAccuracy( spimData, groupingFactors, manualIpLabel, automaticIpLabel, applyCalibration, false, 8 );
		*/
	}
}
