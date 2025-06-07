package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import mpicbg.models.IdentityModel;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealInterval;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.Point1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.PointMatch1D;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntensityCorrection {

	private static final Logger LOG = LoggerFactory.getLogger(IntensityCorrection.class);

	/*private*/ void solveForGlobalCoefficients(
			final Map<ViewId, IntensityTile> coefficientTiles,
			final int iterations) {

		final IntensityTile equilibrationTile = new IntensityTile(IdentityModel::new, new int[] {1, 1, 1}, 1);
		connectTilesWithinPatches(coefficientTiles, equilibrationTile);

		/* optimize */
		final List<IntensityTile> tiles = new ArrayList<>(coefficientTiles.values());

		// anchor the equilibration tile if it is used, otherwise anchor a random tile (the first one)
		final IntensityTile fixedTile;
		final double equilibrationWeight = 0.0; // TODO (equilibration)
		// final double equilibrationWeight = blockData.solveTypeParameters().equilibrationWeight();
		if (equilibrationWeight > 0.0) {
			tiles.add(equilibrationTile);
			fixedTile = equilibrationTile;
		} else {
			fixedTile = tiles.get(0);
		}

		final int numThreads = Runtime.getRuntime().availableProcessors(); // TODO: expose parameter

		LOG.info("solveForGlobalCoefficients: optimizing {} tiles with {} threads", tiles.size(), numThreads);
		final IntensityTileOptimizer optimizer = new IntensityTileOptimizer(0.01, iterations, iterations, 0.75, numThreads);
		optimizer.optimize(tiles, fixedTile);

		coefficientTiles.forEach((tileId, tile) -> {
			tile.updateDistance();

			// TODO (???)
//			final double error = tile.getDistance();
//			final Map<ViewId, Double> errorMap = new HashMap<>();
//			errorMap.put(tileId, error);
//			blockData.getResults().recordAllErrors(tileId, errorMap);
		});

		LOG.info("solveForGlobalCoefficients: exit, returning intensity coefficients for {} tiles", coefficientTiles.size());
	}

	private void connectTilesWithinPatches(
			final Map<ViewId, IntensityTile> coefficientTiles,
			final IntensityTile equilibrationTile) {

		for (final IntensityTile tile : coefficientTiles.values()) {
			final int[] gridSize = tile.getSubTileGridSize();
			final int n = gridSize.length;
			final int[] pos = new int[n];
			final IntervalIterator iter = new IntervalIterator(gridSize);
			while (iter.hasNext()) {
				iter.fwd();
				iter.localize(pos);
				final Tile<?> st0 = tile.getSubTileAt(pos);
				for (int d = 0; d < n; d++) {
					if (pos[d] > 0) {
						pos[d]--;
						final Tile<?> st1 = tile.getSubTileAt(pos);
						pos[d]++;
						identityConnect(st0, st1);
					}
				}
			}
			// TODO (equilibration)
//			final double equilibrationWeight = blockData.solveTypeParameters().equilibrationWeight();
//			if (equilibrationWeight > 0.0) {
//				final List<Double> averages = results.getAveragesFor(p.getTileId());
//				coefficientTile.connectTo(equilibrationTile);
//				for (int i = 0; i < coefficientTile.nSubTiles(); i++) {
//					equilibrateIntensity(coefficientTile.getSubTile(i),
//							equilibrationTile.getSubTile(0),
//							averages.get(i),
//							equilibrationWeight);
//				}
//			}
		}
	}

	private static void equilibrateIntensity(final Tile<?> coefficientTile,
			final Tile<?> equilibrationTile,
			final Double average,
			final double weight) {
		final PointMatch eqMatch = new PointMatch1D(new Point1D(average), new Point1D(0.5), weight);
		coefficientTile.connect(equilibrationTile, Collections.singletonList(eqMatch));
	}

	static protected void identityConnect(final Tile<?> t1, final Tile<?> t2) {
		final ArrayList<PointMatch> matches = new ArrayList<>();
		matches.add(new PointMatch1D(new Point1D(0), new Point1D(0)));
		matches.add(new PointMatch1D(new Point1D(1), new Point1D(1)));
		t1.connect(t2, matches);
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

	static void writeCoefficients(
			final N5Writer n5Writer,
			final String group,
			final String dataset,
			final ViewId viewId,
			final IntensityTile tile
	) {
		final int setupId = viewId.getViewSetupId();
		final int timePointId = viewId.getTimePointId();
		final String path = getCoefficientsDatasetPath(group, dataset, setupId, timePointId);
		System.out.println( "path = " + path );

		final Coefficients coefficients = tile.getScaledCoefficients();
		CoefficientsIO.save( coefficients, n5Writer, path );
	}

	static void writeCoefficients(
			final N5Writer n5Writer,
			final String group,
			final String dataset,
			final Map<ViewId, IntensityTile> coefficientTiles
	) {
		coefficientTiles.forEach((viewId, tile) -> {
			writeCoefficients(n5Writer, group, dataset, viewId, tile);
		});
	}


	// ┌-----------------------------------------
	// │          for BigStitcher-Spark

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
		final IntensityMatcher matcher = new IntensityMatcher(spimData, renderScale, coefficientsSize);
		return new ViewPairCoefficientMatches(viewId1, viewId2, matcher.match(viewId1, viewId2));
	}

	// │          for BigStitcher-Spark
	// └-----------------------------------------
}
