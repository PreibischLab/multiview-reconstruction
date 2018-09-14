package net.preibisch.mvrecon.process.fusion.transformed;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public abstract class AbstractTransformedIntervalRandomAccessible< T extends RealType< T > > implements RandomAccessible< FloatType >
{
	final protected Interval interval;

	final protected FloatType outsideValue;
	final protected long[] boundingBoxOffset;
	final protected Interval boundingBox;

	final protected int n;

	protected InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory = new NLinearInterpolatorFactory< FloatType >();

	public AbstractTransformedIntervalRandomAccessible(
			final Interval interval, // from ImgLoader
			final FloatType outsideValue,
			final Interval boundingBox )
	{
		this.interval = interval;
		this.outsideValue = outsideValue;

		this.n = interval.numDimensions();
		this.boundingBox = boundingBox;
		this.boundingBoxOffset = new long[ n ];

		for ( int d = 0; d < n; ++d )
			this.boundingBoxOffset[ d ] = boundingBox.min( d );
	}

	public void setLinearInterpolation()
	{
		this.interpolatorFactory = new NLinearInterpolatorFactory< FloatType >();
	}

	public void setNearestNeighborInterpolation()
	{
		this.interpolatorFactory = new NearestNeighborInterpolatorFactory< FloatType >();
	}

	@Override
	public RandomAccess< FloatType > randomAccess( final Interval arg0 )
	{
		return randomAccess();
	}

	@Override
	public int numDimensions()
	{
		return n;
	}
}
