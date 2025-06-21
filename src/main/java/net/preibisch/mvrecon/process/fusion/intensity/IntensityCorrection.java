package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealInterval;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.process.fusion.intensity.IntensityMatcher.CoefficientMatch;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

public class IntensityCorrection {

	public static class SerializableRealInterval implements RealInterval, Serializable {

		private static final long serialVersionUID = 1L;

		private final double[] min;
		private final double[] max;

		public SerializableRealInterval(final RealInterval interval) {
			min = interval.minAsDoubleArray();
			max = interval.maxAsDoubleArray();
		}

		public static RealInterval serializable(final RealInterval interval) {
			if (interval instanceof Serializable)
				return interval;
			else
				return new SerializableRealInterval(interval);
		}

		@Override
		public boolean equals(final Object obj) {
			return obj instanceof SerializableRealInterval && Intervals.equals(this, (RealInterval) obj, 0.0);
		}

		@Override
		public int hashCode() {
			return Util.combineHash(Arrays.hashCode(min), Arrays.hashCode(max));
		}

		@Override
		public double realMin(final int d) {
			return min[d];
		}

		@Override
		public double realMax(final int d) {
			return max[d];
		}

		@Override
		public int numDimensions() {
			return min.length;
		}

		@Override
		public String toString() {
			return "SerializableRealInterval{" +
					"min=" + Arrays.toString(min) +
					", max=" + Arrays.toString(max) +
					'}';
		}
	}

	public static RealInterval getBounds(final AbstractSpimData<?> spimData, final ViewId viewId) {
		final TileInfo tile = new TileInfo(new int[] {1, 1, 1}, spimData, viewId);
		return IntensityMatcher.getBounds(tile);
	}

	public static ViewPairCoefficientMatches match(
			final AbstractSpimData<?> spimData,
			final ViewId viewId1,
			final ViewId viewId2,
			final double renderScale,
			final int[] coefficientsSize
	) {
		return match(spimData, viewId1, viewId2, renderScale, coefficientsSize,
				5.0, 250.0, 1000, 1000, 0.02 * 255, 0.1, 10, 3.0);
	}

	public static ViewPairCoefficientMatches match(
			final AbstractSpimData<?> spimData,
			final ViewId viewId1,
			final ViewId viewId2,
			final double renderScale,
			final int[] coefficientsSize,
			final double minIntensity,
			final double maxIntensity,
			final int minNumCandidates,
			final int iterations,
			final double maxEpsilon,
			final double minInlierRatio,
			final int minNumInliers,
			final double maxTrust
	) {
		final IntensityMatcher matcher = new IntensityMatcher(spimData, renderScale, coefficientsSize);
		final List<CoefficientMatch> match = matcher.match(viewId1, viewId2,
				minIntensity, maxIntensity, minNumCandidates, iterations, maxEpsilon,
				minInlierRatio, minNumInliers, maxTrust);
		return new ViewPairCoefficientMatches(viewId1, viewId2, match);
	}

	public static Map<ViewId, Coefficients> solve(
			final int[] coefficientsSize,
			final Collection<ViewPairCoefficientMatches> pairwiseMatches,
			final int iterations
	){
		final IntensitySolver solver = new IntensitySolver(coefficientsSize);
		pairwiseMatches.forEach(solver::connect);
		solver.solveForGlobalCoefficients(iterations);
		final Map<ViewId, IntensityTile> intensityTiles = solver.getIntensityTiles();
		final Map<ViewId, Coefficients> coefficients = new HashMap<>();
		intensityTiles.forEach((k, v) -> coefficients.put(k, v.getCoefficients()));
		return coefficients;
	}

	// TODO: this should become part of SpimData2 I'd say? Ultimately this is a property of the dataset that should also be displayed and potentially used during reconstruction
	public static void writeCoefficients(
			final N5Writer n5Writer,
			final String group,
			final String dataset,
			final Map<ViewId, Coefficients> coefficients
	) {
		coefficients.forEach((viewId, tile) -> {
			final int setupId = viewId.getViewSetupId();
			final int timePointId = viewId.getTimePointId();
			final String path = getCoefficientsDatasetPath(group, dataset, setupId, timePointId);
			CoefficientsIO.save(tile, n5Writer, path);
		});
	}

	public static Coefficients readCoefficients(
			final N5Reader n5Reader,
			final String group,
			final String dataset,
			final ViewId viewId
	) {
		final String path = getCoefficientsDatasetPath(group, dataset, viewId.getViewSetupId(), viewId.getTimePointId());
		return CoefficientsIO.load(n5Reader, path);
	}

	/**
	 * Get N5 path to coefficients for the specified view, as {@code "{group}/setup{setupId}/timepoint{timepointId}/{dataset}"}.
	 */
	static String getCoefficientsDatasetPath(
			final String group,
			final String dataset,
			final int setupId,
			final int timePointId
	) {
		return String.format("%s/setup%d/timepoint%d/%s", group, setupId, timePointId, dataset);
	}
}
