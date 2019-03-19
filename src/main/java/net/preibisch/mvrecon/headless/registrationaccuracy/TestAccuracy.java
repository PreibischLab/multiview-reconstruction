package net.preibisch.mvrecon.headless.registrationaccuracy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import mpicbg.models.AffineModel3D;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOpt;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.strong.InterestPointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestAccuracy
{
	
	public static final String label = "manual";


	public static void printCurrentRegistrationAccuracy( final SpimData2 data )
	{
		// get results independently for angle/illumination combinations
		// or all together
		final Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
		//groupingFactors.add( Illumination.class );
		//groupingFactors.add( Angle.class );
		
		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );
		
		for (final Group< ViewDescription > group : groups)
		{
			final List< ViewDescription > vds = Group.getViewsSorted( group.getViews() );
			for (AtomicInteger i = new AtomicInteger(); i.get()<vds.size(); i.incrementAndGet())
			{
				final RealSum errSum = new RealSum();
				final AtomicInteger count = new AtomicInteger();
				for(AtomicInteger j = new AtomicInteger(); j.get() < vds.size(); j.incrementAndGet())
				{
					if (i.get() == j.get())
						continue;
					
					final InterestPointList ipsA = data.getViewInterestPoints().getViewInterestPointLists( vds.get( i.get() ) ).getInterestPointList( label );
					final InterestPointList ipsB = data.getViewInterestPoints().getViewInterestPointLists( vds.get( j.get() ) ).getInterestPointList( label );

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

						// TODO: apply calibration, rotation
						final ViewRegistration vrA = data.getViewRegistrations().getViewRegistration( vds.get( i.get() ) );
						final ViewRegistration vrB = data.getViewRegistrations().getViewRegistration( vds.get( j.get() ) );
						final AffineTransform3D modelA = vrA.getModel();
						final AffineTransform3D modelB = vrB.getModel();

						modelA.apply( cA, cA );
						modelB.apply( cB, cB );

						final double distance = Util.distance( new RealPoint( cA ), new RealPoint( cB ) );
						errSum.add( distance );
						count.incrementAndGet();
					});
				}
				
				System.out.println( Group.pvid( vds.get( i.get() ) ) + ", mean error : " + (count.get() > 0 ? errSum.getSum() / count.get() : " no correspondences ") );
			}
		}

		
	}

	public static void printManualIpRegistrationAccuracy(SpimData2 data)
	{
		final Set<Class<? extends Entity>> groupingFactors = new HashSet<>(); // NB: may be empty
		//groupingFactors.add( Illumination.class );
		//groupingFactors.add( Angle.class );
		
		final List< Group< ViewDescription > > groups = Group.splitBy( 
				data.getSequenceDescription().getViewDescriptions().values().stream()
					.filter( vd -> !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ))
					.collect( Collectors.toList() ), 
				groupingFactors );
		
		for (final Group< ViewDescription > group : groups)
		{
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
					final InterestPointList ipsA = data.getViewInterestPoints().getViewInterestPointLists( vds.get( i.get() ) ).getInterestPointList( label );
					final InterestPointList ipsB = data.getViewInterestPoints().getViewInterestPointLists( vds.get( j.get() ) ).getInterestPointList( label );

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

						// TODO: apply calibration, rotation
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
	
	public static void main(String[] args)
	{
		SpimData2 spimData = null;
		try
		{
			 spimData = new XmlIoSpimData2( "" ).load( "/Volumes/davidh-ssd/BS_TEST/dataset.xml" );
		}
		catch ( SpimDataException e ){ e.printStackTrace(); }
		
		printCurrentRegistrationAccuracy( spimData );
	}
}
