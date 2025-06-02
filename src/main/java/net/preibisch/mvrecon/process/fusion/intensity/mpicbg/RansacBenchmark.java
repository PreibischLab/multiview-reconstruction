package net.preibisch.mvrecon.process.fusion.intensity.mpicbg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import mpicbg.models.AffineModel1D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.util.BenchmarkHelper;

public class RansacBenchmark {

    public static class ModAffineModel1D extends AffineModel1D
	{
		@Override
		public ModAffineModel1D copy()
		{
			final ModAffineModel1D m = new ModAffineModel1D();
			m.set( this );
			return m;
		}

		@Override
		public < P extends PointMatch >boolean filterRansac(
				final List< P > candidates,
				final Collection< P > inliers,
				final int iterations,
				final double maxEpsilon,
				final double minInlierRatio,
				final int minNumInliers,
				final double maxTrust )
				throws NotEnoughDataPointsException
		{
			final FlattenedMatches flatCandidates = new FlattenedMatches( candidates );
			final MatchIndices flatInliers = new MatchIndices( candidates.size() );

			if ( !ransac( flatCandidates, flatInliers, iterations, maxEpsilon, minInlierRatio, minNumInliers ) )
				return false;
			if ( !filter( flatCandidates, flatInliers, maxTrust, minNumInliers ) )
				return false;

			inliers.clear();
			flatInliers.addSelected( candidates, inliers );
			return true;
		}

		private boolean filter(
				final FlattenedMatches candidates,
				final MatchIndices inliers,
				final double maxTrust,
				final int minNumInliers )
				throws NotEnoughDataPointsException
		{
			if ( inliers.size() < getMinNumMatches() )
				throw new NotEnoughDataPointsException( inliers.size() + " data points are not enough to solve the Model, at least " + getMinNumMatches() + " data points required." );

			final ModAffineModel1D copy = copy();

			// extract PointMatch data into flat arrays
			final int numCandidates = candidates.size();
			final SimpleErrorStatistic observer = new SimpleErrorStatistic( numCandidates );

			int numInliers;
			do
			{
				observer.clear();
				numInliers = inliers.size();
				try
				{
					copy.fit( candidates, inliers );
				}
				catch ( final NotEnoughDataPointsException | IllDefinedDataPointsException e )
				{
					return false;
				}
				final int[] samples = inliers.indices();
				final double[] p = candidates.p()[ 0 ];
				final double[] q = candidates.q()[ 0 ];
				for ( int i = 0; i < numInliers; i++ )
				{
					final int k = samples[ i ];
					final double distance = Math.abs( copy.apply( p[ k ] ) - q[ k ] );
					observer.add( distance );
				}
				final double t = observer.getMedian() * maxTrust;
				int j = 0;
				for ( int i = 0; i < numInliers; i++ )
				{
					final int k = samples[ i ];
					final double distance = Math.abs( copy.apply( p[ k ] ) - q[ k ] );
					if ( distance <= t )
						samples[ j++ ] = k;
				}
				inliers.setSize( j );
				copy.cost = observer.mean();
			}
			while ( numInliers > inliers.size() );

			if ( numInliers < minNumInliers )
				return false;

			set( copy );
			return true;

		}

		@Override
		public < P extends PointMatch > boolean filter(
				final Collection< P > candidates,
				final Collection< P > inliers,
				final double maxTrust,
				final int minNumInliers )
				throws NotEnoughDataPointsException
		{
			// extract PointMatch data into flat arrays
			final FlattenedMatches flatCandidates = new FlattenedMatches( candidates );
			final int numCandidates = flatCandidates.size();

			// equivalent to inliers.addAll( candidates );
			final MatchIndices flatInliers = new MatchIndices( numCandidates );
			flatInliers.setSize( numCandidates );
			Arrays.setAll( flatInliers.indices(), i -> i );

			inliers.clear();
			if ( filter( flatCandidates, flatInliers, maxTrust, minNumInliers ) )
			{
				flatInliers.addSelected( new ArrayList<>( candidates ), inliers );
				return true;
			}
			return false;
		}


//		void fit( FlattenedMatches matches, MatchIndices indices ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
//		{
//			fit( matches, indices, new DataArrays( indices.size() ) );
//		}

		void fit( FlattenedMatches matches, MatchIndices indices ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
		{
			final double[] p = matches.p()[0];
			final double[] q = matches.q()[0];
			final double[] w = matches.w();

			final int size = indices.size();
			final int[] samples = indices.indices();

			if ( size < MIN_NUM_MATCHES )
				throw new NotEnoughDataPointsException( size + " data points are not enough to estimate a 2d affine model, at least " + MIN_NUM_MATCHES + " data points required." );

			double W = 0;
			double S_p = 0;
			double S_q = 0;
			double S_pp = 0;
			double S_pq = 0;

			for ( int i = 0; i < size; i++ )
			{
				final int sample = samples[ i ];
				final double p_i = p[ sample ];
				final double q_i = q[ sample ];
				final double w_i = w[ sample ];
				W += w_i;
				S_p += w_i * p_i;
				S_q += w_i * q_i;
				S_pp += w_i * p_i * p_i;
				S_pq += w_i * p_i * q_i;
			}

			final double a = W * S_pp - S_p * S_p;
			if ( a == 0 )
				throw new IllDefinedDataPointsException();
			m00 = ( W * S_pq - S_p * S_q ) / a;
			m01 = ( S_q - m00 * S_p ) / W;
			invert();

			// assuming all weights = 1 ...
			/*
			double S_p = 0;
			double S_q = 0;
			double S_pp = 0;
			double S_pq = 0;

			for ( int i = 0; i < size; i++ )
			{
				final int sample = samples[ i ];
				final double p_i = p[ sample ];
				final double q_i = q[ sample ];
				S_p += p_i;
				S_q += q_i;
				S_pp += p_i * p_i;
				S_pq += p_i * q_i;
			}

			final double a = size * S_pp - S_p * S_p;
			if ( a == 0 )
				throw new IllDefinedDataPointsException();
			m00 = ( size * S_pq - S_p * S_q ) / a;
			m01 = ( S_q - m00 * S_p ) / size;
			invert();
			*/
		}

		private boolean ransac(
				final FlattenedMatches candidates,
				final MatchIndices inliers,
				final int iterations,
				final double epsilon,
				final double minInlierRatio,
				final int minNumInliers
		) throws NotEnoughDataPointsException
		{
			if ( candidates.size() < getMinNumMatches() )
				throw new NotEnoughDataPointsException( candidates.size() + " data points are not enough to solve the Model, at least " + getMinNumMatches() + " data points required." );

			cost = Double.MAX_VALUE;

			final ModAffineModel1D copy = copy();
			final ModAffineModel1D m = copy();

			final int numCandidates = candidates.size();
			final int numSampled = getMinNumMatches();
			final MatchIndices samples = new MatchIndices( numSampled );
			final MatchIndices tempInliers = new MatchIndices( numCandidates );
			inliers.setSize( 0 );

			int i = 0;
			A:
			while ( i < iterations )
			{
				// choose model.MIN_SET_SIZE disjunctive matches randomly
				samples.sample( rnd, numCandidates );
				try
				{
					m.fit( candidates, samples );
				}
				catch ( final IllDefinedDataPointsException e )
				{
					++i;
					continue;
				}

				int numInliers = 0;

				boolean isGood = m.test( candidates, tempInliers, epsilon, minInlierRatio, minNumInliers );
				while ( isGood && numInliers != tempInliers.size() )
				{
					numInliers = tempInliers.size();
					try
					{
						m.fit( candidates, tempInliers );
					}
					catch ( final IllDefinedDataPointsException e )
					{
						++i;
						continue A;
					}
					isGood = m.test( candidates, tempInliers, epsilon, minInlierRatio, minNumInliers );
				}
				if ( isGood && m.betterThan( copy ) )
				{
					copy.set( m );
					inliers.set( tempInliers );
				}
				++i;
			}

			if ( inliers.size() == 0 )
				return false;

			set( copy );
			return true;
		}

		@Override
		public < P extends PointMatch > boolean ransac(
				final List< P > candidates,
				final Collection< P > inliers,
				final int iterations,
				final double epsilon,
				final double minInlierRatio,
				final int minNumInliers
		) throws NotEnoughDataPointsException
		{
			final FlattenedMatches flatCandidates = new FlattenedMatches( candidates );
			final MatchIndices bestInliers = new MatchIndices( candidates.size() );
			if ( ransac( flatCandidates, bestInliers, iterations, epsilon, minInlierRatio, minNumInliers ) )
			{
				bestInliers.addSelected( candidates, inliers );
				return true;
			}
			return false;
		}

		private double apply( final double l )
		{
			return l * m00 + m01;
		}

		/**
		 * Test the model for a set of PointMatch candidates. Store the indices
		 * of inlier candidates into the given {@code inliers} array and return
		 * the number of inliers.
		 *
		 * @param candidates
		 * 		set of point correspondence candidates
		 * @param inliers
		 * 		will be filled with indices of candidates that fit the model
		 * @param epsilon
		 * 		maximal allowed transfer error
		 * @param minInlierRatio
		 * 		minimal number of inliers to number of candidates
		 *
		 * @return number of inliers (number of valid indices in {@code inliers})
		 */
		private boolean test(
				final FlattenedMatches candidates,
				final MatchIndices inliers,
				final double epsilon,
				final double minInlierRatio,
				final int minNumInliers )
		{
			final int numCandidates = candidates.size();
			final double[] p = candidates.p()[ 0 ];
			final double[] q = candidates.q()[ 0 ];
			final int[] inlierIndices = inliers.indices();
			int i = 0;
			for ( int k = 0; k < numCandidates; k++ )
			{
				final double diff = apply( p[ k ] ) - q[ k ];
				if ( Math.abs( diff ) < epsilon )
				{
					inlierIndices[ i++ ] = k;
				}
			}
			final int numInliers = i;
			inliers.setSize( numInliers );

			final double ir = ( double ) numInliers / ( double ) numCandidates;
			setCost( Math.max( 0.0, Math.min( 1.0, 1.0 - ir ) ) );
			return ( numInliers >= minNumInliers && ir > minInlierRatio );
		}
	}


    public static void main(String[] args) throws IOException, NotEnoughDataPointsException
	{

        final int[][] data = load();
        final int[] p = data[0];
        final int[] q = data[1];

        final int numElements = p.length;

        final List<PointMatch> candidates = new ArrayList<>(numElements);
        for (int i = 0; i < numElements; i++) {
            final double pi = p[i] / 255.0;
            final double qi = q[i] / 255.0;
            final PointMatch pq = new PointMatch(new Point(new double[]{pi}), new Point(new double[]{qi}), 1);
            candidates.add(pq);
        }

		final int iterations = 1000;
		final double  maxEpsilon = 0.1;
		final double minInlierRatio = 0.1;
		final int minNumInliers = 10;
		final double maxTrust = 3.0;

        {
			final AffineModel1D model = new ModAffineModel1D();
//			final AffineModel1D model = new AffineModel1D();
			final PointMatchFilter filter = new RansacRegressionReduceFilter( model );
			final List< PointMatch > inliers = new ArrayList<>();
			filter.filter( candidates, inliers );
			System.out.println( "model = " + model );
        }

		for ( int i = 0; i < 8; i++ )
		{
			BenchmarkHelper.benchmarkAndPrint( 10, false, () -> {
				final AffineModel1D model = new ModAffineModel1D();
//				final AffineModel1D model = new AffineModel1D();
				final PointMatchFilter filter = new RansacRegressionReduceFilter( model );
				final List< PointMatch > inliers = new ArrayList<>();
				filter.filter( candidates, inliers );
//				System.out.println( "model = " + model );
			} );
		}
	}

    public static int[][] load() throws IOException {
        try (
            final InputStream in = RansacBenchmark.class.getResourceAsStream("/ransac.txt");
            final BufferedReader breader = new BufferedReader(new InputStreamReader(in));
        ) {
            final int[][] data = new int[2][];
            for (int j = 0; j < 2; j++) {
                final String[] split = breader.readLine().split(",");
                final int[] ps = new int[split.length];
                for (int i = 0; i < split.length; i++) {
                    ps[i] = Integer.parseInt(split[i].trim());
                }
                data[j] = ps;
            }
            return data;
        }
    }
}
