package net.preibisch.mvrecon.process.fusion.intensity;

import mpicbg.models.AffineModel1D;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.util.BenchmarkHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RansacBenchmark {

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

        BenchmarkHelper.benchmarkAndPrint(10, true,() -> {
            final AffineModel1D model = new AffineModel1D();
            final PointMatchFilter filter = new RansacRegressionReduceFilter(model);
            final List<PointMatch> inliers = new ArrayList<>();
            filter.filter(candidates, inliers);
        });

        final AffineModel1D model = new AffineModel1D();
        final PointMatchFilter filter = new RansacRegressionReduceFilter(model);
        final List<PointMatch> inliers = new ArrayList<>();
        filter.filter(candidates, inliers);
        System.out.println("model = " + model);
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
