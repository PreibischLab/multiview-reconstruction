package net.preibisch.mvrecon.process.fusion.intensity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mpicbg.models.IdentityModel;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.iterator.IntervalIterator;
import net.preibisch.mvrecon.process.fusion.intensity.IntensityMatcher.CoefficientMatch;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FastAffineModel1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.Point1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.PointMatch1D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IntensitySolver {

	private static final Logger LOG = LoggerFactory.getLogger(IntensitySolver.class);

	private final int[] numCoefficients;

	private final Map<ViewId, IntensityTile> intensityTiles = new ConcurrentHashMap<>();

	IntensitySolver(final int[] coefficientsSize) {
		this.numCoefficients = coefficientsSize;
	}

	public void connect(final ViewPairCoefficientMatches matches) {
		connect(matches.view1(), matches.view2(), matches.coefficientMatches());
	}

	public void connect(
			final ViewId p1,
			final ViewId p2,
			final List<CoefficientMatch> coefficientMatches
	) {
		if (coefficientMatches.isEmpty())
			return;

		final IntensityTile p1IntensityTile = getIntensityTile(p1);
		final IntensityTile p2IntensityTile = getIntensityTile(p2);
		for (final CoefficientMatch coefficientMatch : coefficientMatches) {
			final Tile<?> st1 = p1IntensityTile.getSubTileAtIndex(coefficientMatch.coeff1());
			final Tile<?> st2 = p2IntensityTile.getSubTileAtIndex(coefficientMatch.coeff2());
			st1.connect(st2, coefficientMatch.matches());
		}
		p1IntensityTile.connectTo(p2IntensityTile);
	}

	IntensityTile getIntensityTile(final ViewId viewId) {
		final int nFittingCycles = 1; // TODO: expose parameter (?)
		return intensityTiles.computeIfAbsent(viewId, v -> new IntensityTile(FastAffineModel1D::new, numCoefficients, nFittingCycles));
	}

	Map<ViewId, IntensityTile> getIntensityTiles() {
		return intensityTiles;
	}

	public void solveForGlobalCoefficients(final int iterations) {

		final IntensityTile equilibrationTile = new IntensityTile(IdentityModel::new, new int[] {1, 1, 1}, 1);
		connectTilesWithinPatches(intensityTiles, equilibrationTile);

		/* optimize */
		final List<IntensityTile> tiles = new ArrayList<>(intensityTiles.values());

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

		intensityTiles.forEach((tileId, tile) -> {
			tile.updateDistance();

			// TODO (???)
//			final double error = tile.getDistance();
//			final Map<ViewId, Double> errorMap = new HashMap<>();
//			errorMap.put(tileId, error);
//			blockData.getResults().recordAllErrors(tileId, errorMap);
		});

		LOG.info("solveForGlobalCoefficients: exit, returning intensity coefficients for {} tiles", intensityTiles.size());
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

	private static void identityConnect(final Tile<?> t1, final Tile<?> t2) {
		final ArrayList<PointMatch> matches = new ArrayList<>();
		matches.add(new PointMatch1D(new Point1D(0), new Point1D(0)));
		matches.add(new PointMatch1D(new Point1D(1), new Point1D(1)));
		t1.connect(t2, matches);
	}

}