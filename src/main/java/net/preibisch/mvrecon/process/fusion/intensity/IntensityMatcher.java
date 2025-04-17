package net.preibisch.mvrecon.process.fusion.intensity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.blocks.BlockInterval;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.position.transform.Floor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Cast;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.fluent.RandomAccessibleView;

import static net.imglib2.util.Intervals.intersect;
import static net.imglib2.util.Intervals.isEmpty;
import static net.imglib2.view.fluent.RandomAccessibleIntervalView.Extension.border;
import static net.imglib2.view.fluent.RandomAccessibleView.Interpolation.nLinear;

// TODO: Should generics rather be on match method? (after we don't need rendered1 field anymore)
public class IntensityMatcher<T extends NativeType<T> & RealType<T>> {

	private final SpimData spimData;

	private final double renderScale;

	private final int numCoefficients;

	RandomAccessible<IntType> tempCoefficientsMask1;
	RandomAccessible<IntType> tempCoefficientsMask2;

	RandomAccessibleInterval<T> rendered1;
	RandomAccessibleInterval<T> rendered2;

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


		// Next steps:
		// [+] Find best source resolution to render overlap at renderScale.
		// [+] Render tile image and visualize result.
		// [ ] Render coefficient mask for 1 particular coefficient and visualize result
		// [ ] Render both tile image and coefficient mask int coefficient pair intersection only.
		// [ ] Iterate coefficient masks from both tiles and count overlapping voxels.
		// [ ] blk optimizations? E.g., render and match single line from both coefficient masks?

		/*
		// WIP: coefficients mask
		final TileInfo tile = t1;
		final Supplier<BiConsumer<Localizable, ? super IntType>> supplier = () -> {
			final RealPoint cposr = new RealPoint(3);
			final long[] cpos = new long[ 3 ];
			return 	(pos, value) -> {
				t1.coeffBoundsToWorldTransform.applyInverse(cposr,pos);
				Floor.floor(cposr,cpos);
				for ( int d = 0; d < 3; ++d ) {
					if (cpos[d] < 0 || cpos[d] >= tile.numCoeffs[d]) {
						value.set(-1);
						return;
					}
				}
				final int ci = IntervalIndexer.positionToIndex(cpos, tile.numCoeffs);
				value.set(ci);
			};
		};
		final FunctionRandomAccessible<IntType> coeff = new FunctionRandomAccessible<>(3, supplier, IntType::new);
		tempCoefficientsMask = coeff;
		 */

		// For rendering image data in bounding box at appropriate resolution:
		//      - scale by renderScale
		//      - smallest containing Interval
		//      - translation to the min of that interval
		final AffineTransform3D scale = new AffineTransform3D();
		scale.scale(renderScale);

		final FinalRealInterval scaledBounds = scale.estimateBounds(overlap);
		final Interval renderBounds = Intervals.smallestContainingInterval(scaledBounds);

		rendered1 = Cast.unchecked(scaleTile(scale, t1).view().interval(renderBounds));
		rendered2 = Cast.unchecked(scaleTile(scale, t2).view().interval(renderBounds));

		tempCoefficientsMask1 = scaleTileCoefficients(scale, t1);
		tempCoefficientsMask2 = scaleTileCoefficients(scale, t2);

//		final BlockSupplier<T> block = BlockSupplier.of(rendered1);
//		RealViews.affine(t1.getImage(l).view().extend(zero()).interpolate(nLinear()), render);

		System.out.println("overlap = " + overlap);
		System.out.println("renderScale = " + renderScale);
		System.out.println("scaledBounds = " + scaledBounds);
		System.out.println("renderBounds = " + renderBounds);
		System.out.println("Intervals.numElements(renderBounds) = " + Intervals.numElements(renderBounds));

		for (final Pair<CoefficientRegion, CoefficientRegion> pair : pairs) {
			final CoefficientRegion r1 = pair.getA();
			final CoefficientRegion r2 = pair.getB();
			final FinalRealInterval intersection = intersect(r1.wbounds, r2.wbounds);
			final FinalRealInterval scaledIntersection = scale.estimateBounds(intersection);
			final Interval intIntersection = Intervals.largestContainedInterval(scaledIntersection);
//			System.out.println(Intervals.numElements(intIntersection));
		}
	}




	private static RandomAccessible<IntType> scaleTileCoefficients(final AffineTransform3D renderScale, final TileInfo tile) {
		final AffineTransform3D scaleToGrid = new AffineTransform3D();
		scaleToGrid.set(tile.coeffBoundsToWorldTransform.inverse());
		scaleToGrid.concatenate(renderScale.inverse());
		final Supplier<BiConsumer<Localizable, ? super IntType>> supplier = () -> {
			final int[] cpos = new int[ 3 ];
			final BiConsumer<Localizable, int[]> toCoeffGrid = toGrid(scaleToGrid);
			return 	(pos, value) -> {
				toCoeffGrid.accept(pos, cpos);
				for ( int d = 0; d < 3; ++d ) {
					if (cpos[d] < 0 || cpos[d] >= tile.numCoeffs[d]) {
						value.set(-1);
						return;
					}
				}
				final int ci = IntervalIndexer.positionToIndex(cpos, tile.numCoeffs);
				value.set(ci);
			};
		};
		return new FunctionRandomAccessible<>(3, supplier, IntType::new);
	}

	private static BiConsumer<Localizable, int[]> toGrid(AffineTransform3D toGridTransform) {
		final RealPoint gridRealPos = new RealPoint(3);
		return (pos, cpos) -> {
			toGridTransform.apply(pos, gridRealPos);
			cpos[0] = (int) Math.floor(gridRealPos.getDoublePosition(0));
			cpos[1] = (int) Math.floor(gridRealPos.getDoublePosition(1));
			cpos[2] = (int) Math.floor(gridRealPos.getDoublePosition(2));
		};
	}




	// TODO: maybe we should force all tiles to be rendered from the same mipmap level?

	private static RandomAccessible<?> scaleTile(final AffineTransform3D renderScale, final TileInfo tile) {
		final int l = bestMipmapLevel(renderScale, tile);
		System.out.println("l = " + l);
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(renderScale);
		transform.concatenate(tile.model);
		transform.concatenate(tile.getMipmapTransforms()[l]);
		return tile.getImage(l).view()
				.extend(border())
				.interpolate(nLinear())
				.use(affine(transform));
	}

	private static <T> Function<RealRandomAccessible<T>, RandomAccessibleView<T, ?>> affine(final AffineTransform3D transform) {
		return rra -> RealViews.affine(rra, transform).view();
	}

	// TODO: Think about this again!
	//
	//       What is a good mipmap level to use? Closest to render resolution. Better too coarse then too fine.
	//       What about anisotropy?
	//       What about adding our own downsampling on top of the existing mipmap
	//
 	//       For now, I just take the smallest side lengths of a transformed source voxel in render space.
	//       That should be larger than 1, but as small as possible.
	private static int bestMipmapLevel(final AffineTransform3D renderScale, final TileInfo tile) {
		final AffineTransform3D transform = new AffineTransform3D();
		final AffineTransform3D[] mipmaps = tile.getMipmapTransforms();

		final float acceptedError = 0.02f;

		int bestLevel = 0;
		double bestSize = 0;
		for (int i = 0; i < mipmaps.length; i++) {
			transform.set(renderScale);
			transform.concatenate(tile.model);
			transform.concatenate(mipmaps[i]);
			final double[] step = getStepSize(transform);
			final double pixelSize = Math.min(step[0], Math.min(step[1], step[2]));
			if (bestSize < (1 - acceptedError) && pixelSize > bestSize) {
				bestSize = pixelSize;
				bestLevel = i;
			} else if (pixelSize > (1 - acceptedError) && pixelSize < bestSize) {
				bestSize = pixelSize;
				bestLevel = i;
			}
		}
		return bestLevel;
	}

	/**
	 * Compute the projected voxel size at the given transform. That is, when
	 * moving by 1 in each dimension of the sources space, how far do we move in
	 * the target space of the transform.
	 */
	//
	// TODO: This is copied from DownsampleTools. Make DownsampleTools::getStepSize public?
	//       Probably better to put it into bigdataviewr-core and reuse it from there?
	private static double[] getStepSize(final AffineTransform3D model) {
		final double[] size = new double[3];
		final double[] tmp = new double[3];
		for (int d = 0; d < 3; ++d) {
			for (int i = 0; i < 3; ++i)
				tmp[i] = model.get(i, d);
			size[d] = LinAlgHelpers.length(tmp);
		}
		return size;
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

		final List<CoefficientRegion> coefficientRegions = new ArrayList<>();
		for (int i = 0; i < ncoeff; ++i) {
			IntervalIndexer.indexToPosition(i, tile.numCoeffs, cmin);
			final FinalRealInterval wbounds = tile.coeffBoundsToWorldTransform.estimateBounds(cbounds);
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
}