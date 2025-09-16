package net.preibisch.mvrecon.process.fusion.intensity;

import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.mask.Masked;
import net.imglib2.type.numeric.NumericType;

import static bdv.viewer.Interpolation.NEARESTNEIGHBOR;
import static net.imglib2.view.fluent.RandomAccessibleIntervalView.Extension.zero;
import static net.imglib2.view.fluent.RandomAccessibleView.Interpolation.clampingNLinear;
import static net.imglib2.view.fluent.RandomAccessibleView.Interpolation.nearestNeighbor;

class IntensityCorrectedSource<T extends NativeType<T> & NumericType<T>> extends WrappedSource<T> implements Source<T> {

	/**
	 * Maps timepoint index to coefficients
	 */
	private final IntFunction<Coefficients> timepointToCoefficients;

	private final BooleanSupplier enableIntensityCorrection;

	private int currentTimePointIndex = -1;

	private boolean currentTimePointIsPresent;

	private final RandomAccessibleInterval<T>[] currentImgs;

	/**
	 * TODO: fix javadoc (this is jsut copied from TransformedSource
	 * <p>
	 * Instantiates a new {@link TransformedSource} wrapping the specified
	 * source with the identity transform.
	 *
	 * @param source
	 * 		the source to wrap.
	 */
	IntensityCorrectedSource(
			final Source<T> source,
			final IntFunction<Coefficients> timepointToCoefficients,
			final BooleanSupplier enableIntensityCorrection
	) {
		super(source);
		this.timepointToCoefficients = timepointToCoefficients;
		this.currentImgs = new RandomAccessibleInterval[source.getNumMipmapLevels()];
		this.enableIntensityCorrection = enableIntensityCorrection;
	}

	private void loadTimepoint(final int timepointIndex) {

		currentTimePointIndex = timepointIndex;
		currentTimePointIsPresent = delegate.isPresent(timepointIndex);

		if (currentTimePointIsPresent) {
			final Coefficients coefficients = timepointToCoefficients.apply(timepointIndex);
			for (int level = 0; level < currentImgs.length; ++level) {
				final RandomAccessibleInterval<T> image = delegate.getSource(timepointIndex, level);
				final int[] cellDimensions;
				if (image instanceof AbstractCellImg) {
					cellDimensions = ((AbstractCellImg<?, ?, ?, ?>) image).getCellGrid().getCellDimensions();
				} else {
					cellDimensions = new int[] {64};
				}

				// TODO: For volatile, we will use the non-volatile source, do the same as here,
				//       then use VolatileViews.wrap(...).
				//       -- Hopefully, the BlockSupplier.toCellImg uses Volatile Accesses.
				//       We will use a new or supplied-on-construction
				//       SharedQueue. That should be added to cache
				//       management (for nextFrame).

				final RandomAccessibleInterval<T> corrected = BlockSupplier.of(image)
						.andThen(FastLinearIntensityMap.linearIntensityMap(coefficients, image))
						.toCellImg(image.dimensionsAsLongArray(), cellDimensions);
				currentImgs[level] = corrected;
			}
		} else {
			Arrays.setAll(currentImgs, null);
		}
	}

	@Override
	public synchronized RandomAccessibleInterval<T> getSource(final int t, final int level) {
		if (enableIntensityCorrection.getAsBoolean()) {
			if (t != currentTimePointIndex)
				loadTimepoint(t);
			return currentImgs[level];
		} else {
			return delegate.getSource(t, level);
		}
	}

	@Override
	public synchronized RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method) {
		if (enableIntensityCorrection.getAsBoolean()) {
			if (t != currentTimePointIndex)
				loadTimepoint(t);
			if (!currentTimePointIsPresent)
				return null;
			return getSource(t, level)
					.view()
					.extend(zero())
					.interpolate(method == NEARESTNEIGHBOR ? nearestNeighbor() : clampingNLinear());
		} else {
			return delegate.getInterpolatedSource(t, level, method);
		}
	}

	@Override
	public RandomAccessibleInterval<? extends Masked<T>> getMaskedSource(final int t, final int level) {
		// TODO: Eventually we should handle masked images in BlockSupplier, but
		//       it is unclear what that should look like exactly. To make it
		//       work somehow, now, I would combine the mask from the delegate
		//       source with the value of the (unmasked) getSource().
		return delegate.getMaskedSource(t, level);
	}

	@Override
	public RealRandomAccessible<? extends Masked<T>> getInterpolatedMaskedSource(final int t, final int level, final Interpolation method) {
		// TODO: see above
		return delegate.getInterpolatedMaskedSource(t, level, method);
	}

	@Override
	public T getType() {
		return delegate.getType();
	}
}
