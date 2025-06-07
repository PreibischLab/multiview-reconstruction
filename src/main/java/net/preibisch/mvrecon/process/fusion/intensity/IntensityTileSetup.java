package net.preibisch.mvrecon.process.fusion.intensity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.process.fusion.intensity.IntensityMatcher.CoefficientMatch;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FastAffineModel1D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class IntensityTileSetup {

	private static final Logger LOG = LoggerFactory.getLogger(IntensityTileSetup.class);

	private final int[] numCoefficients;

	private final Map<ViewId, IntensityTile> intensityTiles = new ConcurrentHashMap<>();

	IntensityTileSetup(final int[] coefficientsSize) {
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
}