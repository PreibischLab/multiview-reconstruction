package net.preibisch.mvrecon.process.fusion.transformed;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;

public class TransformedRasteredRealRandomAccessible< T > implements RandomAccessible< T >
{
	final RealRandomAccessible< T > realRandomAccessible;
	final T zero;
	final AffineTransform3D transform;
	final long[] offset;

	/**
	 * @param realRandomAccessible - some {@link RealRandomAccessible} that we transform
	 * @param transform - the affine transformation
	 * @param offset - an additional translational offset
	 * @param zero - the zero constant
	 */
	public TransformedRasteredRealRandomAccessible(
			final RealRandomAccessible< T > realRandomAccessible,
			final T zero,
			final AffineTransform3D transform,
			final long[] offset )
	{
		this.realRandomAccessible = realRandomAccessible;
		this.zero = zero;
		this.transform = transform;
		this.offset = offset;
	}

	@Override
	public int numDimensions() { return realRandomAccessible.numDimensions(); }

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new TransformedRasteredRealRandomAccess< T >( realRandomAccessible, zero, transform, Util.long2int( offset ) );
	}

	@Override
	public RandomAccess< T > randomAccess( final Interval interval ) { return randomAccess(); }
}
