package net.preibisch.mvrecon.process.fusion.intensity.mpicbg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import net.imglib2.util.BenchmarkHelper;

public class RansacBenchmark {

	public static void main(String[] args) throws IOException, NotEnoughDataPointsException
	{

        final int[][] data = load();
        final int[] p = data[0];
        final int[] q = data[1];

        final int numElements = p.length;

//        final List<PointMatch> candidates = new ArrayList<>(numElements);
		final FlattenedMatches flatCandidates = new FlattenedMatches( 1, numElements );
		flatCandidates.setWeighted( false );
        for (int i = 0; i < numElements; i++) {
            final double pi = p[i] / 255.0;
            final double qi = q[i] / 255.0;
			flatCandidates.put( pi, qi, 1 );
        }
		flatCandidates.flip();


		final int iterations = 1000;
		final double  maxEpsilon = 0.1;
		final double minInlierRatio = 0.1;
		final int minNumInliers = 10;
		final double maxTrust = 3.0;

        {
			final FastAffineModel1D model = new FastAffineModel1D();
			final RansacRegressionReduceFilter filter = new RansacRegressionReduceFilter( model, iterations, maxEpsilon, minInlierRatio, minNumInliers, maxTrust );
			final List< PointMatch > inliers = new ArrayList<>();
			filter.filter( flatCandidates, inliers );
			System.out.println( "model = " + model );
        }

		for ( int i = 0; i < 8; i++ )
		{
			BenchmarkHelper.benchmarkAndPrint( 10, false, () -> {
				final FastAffineModel1D model = new FastAffineModel1D();
				final RansacRegressionReduceFilter filter = new RansacRegressionReduceFilter( model, iterations, maxEpsilon, minInlierRatio, minNumInliers, maxTrust );
				final List< PointMatch > inliers = new ArrayList<>();
				filter.filter( flatCandidates, inliers );
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
