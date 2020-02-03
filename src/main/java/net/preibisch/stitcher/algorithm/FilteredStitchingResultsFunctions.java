package net.preibisch.stitcher.algorithm;

import net.imglib2.RealPoint;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.PairwiseStitchingResult;

public class FilteredStitchingResultsFunctions {
	public static interface Filter
	{
		public <C extends Comparable< C >> boolean conforms(final PairwiseStitchingResult< C > result);
	}
	
	public static class CorrelationFilter implements Filter
	{
		private final double minR;
		private final double maxR;
		public CorrelationFilter(final double minR, final double maxR)
		{
			this.minR = minR;
			this.maxR = maxR;
		}

		@Override
		public <C extends Comparable< C >> boolean conforms(final PairwiseStitchingResult< C > result)
		{
			return (result.r() <= maxR) && (result.r() >= minR);
		}
	}

	public static class AbsoluteShiftFilter implements Filter
	{
		private final double[] minMaxShift;
		public AbsoluteShiftFilter(double[] minMaxShift)
		{
			this.minMaxShift = minMaxShift;
		}

		@Override
		public <C extends Comparable< C >> boolean conforms(final PairwiseStitchingResult< C > result)
		{
			double[] v = new double[result.getTransform().numDimensions()];
			result.getTransform().apply( v, v );

			// negative means at least that shift, positive means less than this shift is allowed
			for (int d = 0; d < v.length; d++)
			{
				if ( minMaxShift[d] >= 0 && Math.abs( v[d] ) > minMaxShift[d] )
					return false;
				if ( minMaxShift[d] < 0 && Math.abs( v[d] ) < -minMaxShift[d] )
					return false;
			}
			return true;
		}
	}
	
	public static class ShiftMagnitudeFilter implements Filter
	{
		private final double maxShift;

		public ShiftMagnitudeFilter(double maxShift)
		{
			this.maxShift = maxShift;
		}

		@Override
		public <C extends Comparable< C >> boolean conforms(PairwiseStitchingResult< C > result)
		{
			double[] v = new double[result.getTransform().numDimensions()];
			double[] vt = new double[result.getTransform().numDimensions()];
			result.getTransform().apply( v, vt );

			return Util.distance( new RealPoint( v ), new RealPoint( vt ) ) <= maxShift;
		}
	}
	
}
