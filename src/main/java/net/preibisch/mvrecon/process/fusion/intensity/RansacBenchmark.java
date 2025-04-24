package net.preibisch.mvrecon.process.fusion.intensity;

import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel1D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.util.BenchmarkHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

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

		static class FlattenedMatches
		{
			final int size;
			final double[] p;
			final double[] q;
			final double[] w;

			FlattenedMatches( final int size )
			{
				this.size = size;
				p = new double[ size ];
				q = new double[ size ];
				w = new double[ size ];
			}

			< P extends PointMatch > FlattenedMatches( final List< P > candidates )
			{
				this( candidates.size() );
				for ( int i = 0; i < candidates.size(); i++ )
				{
					P match = candidates.get( i );
					p[ i ] = match.getP1().getL()[ 0 ];
					q[ i ] = match.getP2().getL()[ 0 ];
					w[ i ] = match.getWeight();
				}
			}
		}

		// TODO: minimal bounds checking
		static class MatchIndices
		{
			final int[] indices;
			int size;

			MatchIndices( final int capacity )
			{
				indices = new int[ capacity ];
				size = 0;
			}

			int size()
			{
				return size;
			}

			void set( final MatchIndices indices )
			{
				System.arraycopy( indices.indices, 0, this.indices, 0, indices.size );
				this.size = indices.size;
			}
		}

		int numFits = 0;

		void fit( FlattenedMatches matches, MatchIndices indices ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
		{
			final double[] p = matches.p;
			final double[] q = matches.q;
			final double[] w = matches.w;

			final int size = indices.size;
			final int[] samples = indices.indices;

			final double[] psX = new double[ size ];
			final double[] qsX = new double[ size ];
			final double[] ws = new double[ size ];
			for ( int i = 0; i < size; i++ )
			{
				final int sample = samples[ i ];
				psX[ i ] = p[ sample ];
				qsX[ i ] = q[ sample ];
				ws[ i ] = w[ sample ];
			}
			System.out.println( "numFits = " + ++numFits );

			fit( new double[][] { psX }, new double[][] { qsX }, ws );
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

			if ( candidates.size() < getMinNumMatches() )
				throw new NotEnoughDataPointsException( candidates.size() + " data points are not enough to solve the Model, at least " + getMinNumMatches() + " data points required." );

			cost = Double.MAX_VALUE;

			final ModAffineModel1D copy = copy();
			final ModAffineModel1D m = copy();

			inliers.clear();

			// extract everything into flat arrays
			final FlattenedMatches flatCandidates = new FlattenedMatches( candidates );
			final int numCandidates = flatCandidates.size;

			final int numSamples = getMinNumMatches();
			final MatchIndices samples = new MatchIndices( numSamples );
			samples.size = numSamples;

			final MatchIndices bestInliers = new MatchIndices( numCandidates );
			final MatchIndices tempInliers = new MatchIndices( numCandidates );

			int numFits1 = 0;
			int numFits2 = 0;

			int i = 0;
			A:
			while ( i < iterations )
			{
				// choose model.MIN_SET_SIZE disjunctive matches randomly
				distinctRandomInts( rnd, numCandidates, samples.indices );
				try
				{
					System.out.println( "numFits1 = " + ++numFits1 );
					m.fit( flatCandidates, samples );
				}
				catch ( final IllDefinedDataPointsException e )
				{
					++i;
					continue;
				}

				int numInliers = 0;

				boolean isGood = m.test( flatCandidates, tempInliers, epsilon, minInlierRatio );
				while ( isGood && numInliers != tempInliers.size() )
				{
					numInliers = tempInliers.size();
					try
					{
						System.out.println( "numFits2 = " + ++numFits2 );
						m.fit( flatCandidates, tempInliers );
					}
					catch ( final IllDefinedDataPointsException e )
					{
						++i;
						continue A;
					}
					isGood = m.test( flatCandidates, tempInliers, epsilon, minInlierRatio );
				}
				if ( isGood && m.betterThan( copy ) )
				{
					copy.set( m );
					bestInliers.set( tempInliers );
				}
				++i;
			}

			if ( bestInliers.size() == 0 )
				return false;

			set( copy );
			for ( int j = 0; j < bestInliers.size(); ++j )
				inliers.add( candidates.get( bestInliers.indices[ j ] ) );
			return true;
		}

		private static void distinctRandomInts( final Random rnd, final int bound, int[] ints )
		{
			if ( ints.length > bound )
			{
				throw new IllegalArgumentException( "not enough candidates" );
			}
			for ( int count = 0; count < ints.length; )
			{
				final int value = rnd.nextInt( bound );
				if ( !contains( ints, count, value ) )
				{
					ints[ count++ ] = value;
				}
			}
		}

		private static boolean contains( final int[] ints, final int bound, final int value )
		{
			for ( int i = 0; i < bound; i++ )
			{
				if ( ints[ i ] == value )
				{
					return true;
				}
			}
			return false;
		}

		// CONTINUE HERE:
		// TODO: Implement AbstractModel.test(...) alternative for this:
		//       boolean isGood = m.test( candidates, tempInliers, epsilon, minInlierRatio );

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
				final double minInlierRatio )
		{
			final int numCandidates = candidates.size;
			final double[] p = candidates.p;
			final double[] q = candidates.q;
			final double squEpsilon = epsilon * epsilon;
			int i = 0;
			for ( int k = 0; k < numCandidates; k++ )
			{
				final double diff = apply( p[ k ] ) - q[ k ];
				final double squDist = diff * diff;
				if ( squDist < squEpsilon )
				{
					inliers.indices[ i++ ] = k;
				}
			}
			final int numInliers = i;
			inliers.size = numInliers;

			final int minNumInliers = getMinNumMatches();
			final double ir = ( double ) numInliers / ( double ) numCandidates;
			setCost( Math.max( 0.0, Math.min( 1.0, 1.0 - ir ) ) );
			return ( numInliers >= minNumInliers && ir > minInlierRatio );
		}
	}


    public static void main(String[] args) throws IOException {

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

        {
            final AffineModel1D model = new ModAffineModel1D();
//            final AffineModel1D model = new AffineModel1D();
            final PointMatchFilter filter = new RansacRegressionReduceFilter(model);
            final List<PointMatch> inliers = new ArrayList<>();
            filter.filter(candidates, inliers);
            System.out.println("model = " + model);
        }
/*
        BenchmarkHelper.benchmarkAndPrint(10, true,() -> {
//            final AffineModel1D model = new ModAffineModel1D();
            final AffineModel1D model = new AffineModel1D();
            final PointMatchFilter filter = new RansacRegressionReduceFilter(model);
            final List<PointMatch> inliers = new ArrayList<>();
            filter.filter(candidates, inliers);
        });
*/
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
