package net.preibisch.mvrecon.process.fusion.intensity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.blocks.BlockInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

import static net.imglib2.util.Intervals.intersect;
import static net.imglib2.util.Intervals.isEmpty;

public class IntensityMatcher {

	private final SpimData spimData;

	private final double renderScale;

	private final int numCoefficients;

	IntensityMatcher(
			final SpimData spimData,
			final double renderScale,
			final int numCoefficients
	) {
		this.spimData = spimData;
		this.renderScale = renderScale;
		this.numCoefficients = numCoefficients;
	}

	public void match(
			final ViewId p1,
			final ViewId p2
			//final HashMap<String, ArrayList<Tile<? extends Affine1D<?>>>> coefficientTiles
	) {
		// Get bounding box
		//      Should this later be an argument, for when we want to parallelize over blocks?
		//		Do we want to parallelize over blocks? Maybe the matches collection...
		//
		//		Should the bounding box be in global coordinates? Given that
		//      we pick the resolution level ourselves, that would make sense.
		//      (Actually, we might even pick a different level for each ViewId).
		//
		//      Maybe in global coordinates scaled by renderscale. I think that makes sense.
		//      So a 64x64x64 bounding box means that this many pixels are computed...



		final TileInfo t1 = new TileInfo(numCoefficients, spimData, p1);
		final TileInfo t2 = new TileInfo(numCoefficients, spimData, p2);

		// This is the region we need to look at.
		final RealInterval overlap = getOverlap(t1, t2);

		// Now find out for which CoefficientRegions (transformed into global
		// coordinates) the intersection with overlap is non-empty. Those are
		// candidate regions for matching between tiles.
		final List<CoefficientRegion> r1s = overlappingCoefficientRegions(t1, overlap);
		final List<CoefficientRegion> r2s = overlappingCoefficientRegions(t2, overlap);

		// Find intersecting CoefficientRegion pairs.
		final List<Pair<CoefficientRegion, CoefficientRegion>> pairs = new ArrayList<>();
		for (final CoefficientRegion r1 : r1s) {
			for (final CoefficientRegion r2 : r2s) {
				if (!isEmpty(intersect(r1.wbounds, r2.wbounds))) {
					pairs.add(new ValuePair<>(r1, r2));
				}
			}
		}
		System.out.println("found " + pairs.size() + " CoefficientRegion pairs.");


		// For rendering image data in bounding box at appropriate resolution:
		//      - scale by renderScale
		//      - smallest containing Interval
		//      - translation to the min of that interval
		final AffineTransform3D scale = new AffineTransform3D();
		scale.scale(renderScale);
		final FinalRealInterval scaledBounds = scale.estimateBounds(overlap);
		System.out.println("scaledBounds = " + scaledBounds);

	}

	/**
	 * Loop over num of coefficient regions.
	 * Use {@code IntervalIndexer} to get un-flattened coordinate c.
	 * Make (c,c+1) interval and transform to world coordinates.
	 * Intersect with {@code bounds} and collect overlapping coefficients into List.
	 */
	private static List<CoefficientRegion> overlappingCoefficientRegions(
			final TileInfo tile,
			final RealInterval bounds // in world coordinates
	) {
		final int ncoeff = (int) Intervals.numElements(tile.numCoeffs);
		final long[] cmin = new long[3];
		final int[] csize = {2, 2, 2}; // cbounds is used as a RealInterval and we want cmax[d] == cmin[d] + 1
		final Interval cbounds = BlockInterval.wrap(cmin, csize);

		// TODO: put this into TileInfo.coeffBoundsToWorldTransform
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(tile.model);
		transform.concatenate(tile.coeffBoundsTransform);

		final List<CoefficientRegion> coefficientRegions = new ArrayList<>();
		for (int i = 0; i < ncoeff; ++i) {
			IntervalIndexer.indexToPosition(i, tile.numCoeffs, cmin);
			final FinalRealInterval wbounds = transform.estimateBounds(cbounds);
			if (!isEmpty(intersect(bounds, wbounds))) {
				coefficientRegions.add(new CoefficientRegion(i, wbounds));
			}
		}
		return coefficientRegions;
	}

	static class CoefficientRegion {

		final int index;
		final RealInterval wbounds;

		CoefficientRegion(final int index, final RealInterval wbounds) {
			this.index = index;
			this.wbounds = wbounds;
		}

		@Override
		public String toString() {
			return "CoefficientRegion{index=" + index + ", wbounds=" + wbounds + '}';
		}
	}


	// --- - - -  -  -   -   U T I L   -   -  -  - - - ---

	/**
	 * Returns the bounding interval of the specified {@code views} in global coordinates.
	 */
	public static RealInterval getOverlap(final TileInfo tile1, TileInfo tile2) {
		return intersect(getBounds(tile1), getBounds(tile2));
	}

	/**
	 * Returns the bounding interval of the specified {@code view} in global coordinates.
	 * This assumes the bounding box in images space is from min-0.5 to max+0.5.
	 */
	public static RealInterval getBounds(TileInfo tile) {
		return tile.model.estimateBounds(imageBounds(tile.dimensions));
	}

	/**
	 * Convert integer {@code dimensions} into a bounding box {@code RealInterval} from
	 * {@code -0.5} to {@code max + 0.5} in every dimension.
	 */
	public static RealInterval imageBounds(final Dimensions dimensions) {
		final int n = dimensions.numDimensions();
		final double[] min = new double[n];
		final double[] max = new double[n];
		Arrays.fill(min, -0.5);
		Arrays.setAll(max, d -> dimensions.dimension(d) - 0.5);
		return FinalRealInterval.wrap(min, max);
	}

//	public static RealInterval coeffBounds(final int[] ncoeff, final int[] ) {}



}