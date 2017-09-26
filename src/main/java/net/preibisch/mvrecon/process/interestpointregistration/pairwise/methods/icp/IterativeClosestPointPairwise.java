package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.icp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import mpicbg.icp.ICP;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.TranslationModel3D;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;

/**
 * Iterative closest point implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IterativeClosestPointPairwise< I extends InterestPoint > implements MatcherPairwise< I >
{
	final IterativeClosestPointParameters ip;

	public IterativeClosestPointPairwise( final IterativeClosestPointParameters ip  )
	{
		this.ip = ip;
	}

	@Override
	public PairwiseResult< I > match( final List< I > listAIn, final List< I > listBIn )
	{
		final PairwiseResult< I > result = new PairwiseResult< I >( true );

		final ArrayList< I > listA = new ArrayList<>();
		final ArrayList< I > listB = new ArrayList< I >();

		for ( final I i : listAIn )
			listA.add( i );

		for ( final I i : listBIn )
			listB.add( i );

		// identity transform
		Model<?> model = this.ip.getModel();

		if ( listA.size() < model.getMinNumMatches() || listB.size() < model.getMinNumMatches() )
		{
			result.setResult( System.currentTimeMillis(), "Not enough detections to match" );
			result.setCandidates( new ArrayList< PointMatchGeneric< I > >() );
			result.setInliers( new ArrayList< PointMatchGeneric< I > >(), Double.NaN );
			return result;
		}

		final ICP< I > icp = new ICP< I >( listA, listB, (float)ip.getMaxDistance() );

		int i = 0;
		double lastAvgError = 0;
		int lastNumCorresponding = 0;

		boolean converged = false;

		do
		{
			try
			{
				icp.runICPIteration( model, model );
			}
			catch ( NotEnoughDataPointsException e )
			{
				failWith( result, "ICP", "NotEnoughDataPointsException", e );
			}
			catch ( IllDefinedDataPointsException e )
			{
				failWith( result, "ICP", "IllDefinedDataPointsException", e );
			}
			catch ( NoSuitablePointsException e )
			{
				failWith( result, "ICP", "NoSuitablePointsException", e );
			}

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();
			
			System.out.println( i + ": " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + ", max error [px] " + icp.getMaximalError() );
		}
		while ( !converged && ++i < ip.getMaxNumIterations() );

		if ( icp.getPointMatches() == null )
		{
			result.setCandidates( new ArrayList<>() );
			result.setInliers( new ArrayList<>(), Double.NaN );
			result.setResult( System.currentTimeMillis(), "No corresponding points." );
		}
		else
		{
			result.setCandidates( ICP.unwrapPointMatches( icp.getPointMatches() ) );
			result.setInliers( ICP.unwrapPointMatches( icp.getPointMatches() ), icp.getAverageError() );
			result.setResult( System.currentTimeMillis(), "Found " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + " after " + i + " iterations" );
		}
		
		return result;
	}

	public static < I extends InterestPoint > void failWith( final PairwiseResult< I > result, final String algo, final String exType, final Exception e )
	{
		result.setResult( System.currentTimeMillis(), algo + " failed with " + exType + " matching " );
		
		result.setCandidates( new ArrayList< PointMatchGeneric< I > >() );
		result.setInliers( new ArrayList< PointMatchGeneric< I > >(), Double.NaN );
	}

	public static void main( final String[] args ) throws Exception
	{
		// test ICP
		final ArrayList< InterestPoint > listA = new ArrayList< InterestPoint >();
		final ArrayList< InterestPoint > listB = new ArrayList< InterestPoint >();
		
		listA.add( new InterestPoint( 0, new double[]{ 10, 10, 0 } ) );
		listB.add( new InterestPoint( 0, new double[]{ 11, 13, 0 } ) ); // d = 3.16

		final Random rnd = new Random( 43534 );
		final float maxError = 4;

		for ( int i = 0; i < 5; ++i )
		{
			final float x = rnd.nextFloat() * 10000 + 150;
			final float y = rnd.nextFloat() * 10000 + 150;
			
			listA.add( new InterestPoint( i, new double[]{ x, y, 0 } ) );
			listB.add( new InterestPoint( i, new double[]{ x + 2, y + 4, 0 } ) ); // d = 4.472, will be less than 4 once the first one matched
		}

		// use the world and not the local coordinates
		for ( int i = 0; i < listA.size(); ++ i )
		{
			listA.get( i ).setUseW( true );
			listB.get( i ).setUseW( true );
			IOFunctions.println( Util.printCoordinates( listA.get( i ).getL() ) + " >>> " + Util.printCoordinates( listB.get( i ).getL() ) + ", d=" + Point.distance( listA.get( i ), listB.get( i ) ) );
		}

		final ICP< InterestPoint > icp = new ICP< InterestPoint >( listA, listB, maxError );
		
		// identity transform
		TranslationModel3D model = new TranslationModel3D();

		int i = 0;
		double lastAvgError = 0;
		int lastNumCorresponding = 0;

		boolean converged = false;

		do
		{
			System.out.println( "\n" + i );
			System.out.println( "lastModel: " + model.toString() );

			try
			{
				icp.runICPIteration( model, model );
			}
			catch ( NotEnoughDataPointsException e )
			{
				throw new NotEnoughDataPointsException( e );
			}
			catch ( IllDefinedDataPointsException e )
			{
				throw new IllDefinedDataPointsException( e );
			}
			catch ( NoSuitablePointsException e )
			{
				throw new NoSuitablePointsException( e.toString() );
			}

			System.out.println( "newModel: " + model.toString() );

			System.out.println( "lastError: " + lastAvgError + ", lastNumCorresponding: " + lastNumCorresponding );
			System.out.println( "thisError: " + icp.getAverageError() + ", thisNumCorresponding: " + icp.getAverageError() );

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();
			
			System.out.println( i + ": " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + ", max error [px] " + icp.getMaximalError() );
		}
		while ( !converged && ++i < 100 );
		

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Found " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + " after " + i + " iterations" );
	}

	/**
	 * We modify w[] on these points, which is not a good idea when running things multithreaded
	 */
	@Override
	public boolean requiresInterestPointDuplication() { return true; }
}
