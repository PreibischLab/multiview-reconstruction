package net.preibisch.mvrecon.process.fusion.intensity;

import static net.imglib2.util.Util.safeInt;

import java.util.Arrays;

import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

/**
 * Holds flattened coefficient array and dimensions.
 */
public class Coefficients {

	private final int[] size;
	private final int[] strides;

	/**
	 * {@code flattenedCoefficients[i]} holds the flattened array of the i-the coefficient.
	 * That is, for linear map {@code y=a*x+b}, {@code flattenedCoefficients[0]} holds all the {@code a}s and
	 * {@code flattenedCoefficients[1]} holds all the {@code b}s.
	 */
	final double[][] flattenedCoefficients;

	public Coefficients(
			final double[][] coefficients,
			final int... fieldDimensions) {

		if (coefficients == null)
			throw new NullPointerException();

		final int numCoefficients = coefficients.length;
		if (numCoefficients == 0)
			throw new IllegalArgumentException();

		final int numElements = safeInt(Intervals.numElements(fieldDimensions));
		for (final double[] coefficient : coefficients)
			if (coefficient.length != numElements)
				throw new IllegalArgumentException();

		size = fieldDimensions.clone();
		strides = IntervalIndexer.createAllocationSteps(size);
		flattenedCoefficients = new double[numCoefficients][];
		Arrays.setAll(flattenedCoefficients, i -> coefficients[i].clone());
	}

	int size(final int d) {
		return size[d];
	}

	int stride(final int d) {
		return strides[d];
	}

	int numDimensions() {
		return size.length;
	}

	int numCoefficients() {
		return flattenedCoefficients.length;
	}
}
