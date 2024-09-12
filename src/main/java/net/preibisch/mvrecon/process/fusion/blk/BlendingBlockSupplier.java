package net.preibisch.mvrecon.process.fusion.blk;

import net.imglib2.Interval;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;

public class BlendingBlockSupplier implements BlockSupplier< FloatType >
{
	private final Blending blending;

	/**
	 * Conceptually,the given {@code interval} is filled with blending weights, then transformed with {@code transform}.
	 * <p>
	 * Blending weights are {@code 0 <= w <= 1}.
	 * <p>
	 * Weights are {@code w=0} for the outermost {@code border} pixels of {@code interval} (and outside of {@code interval}).
	 * Then weights transition from {@code 0<=w<=1} over {@code blending} pixels.
	 * Weights are {@code w=1} inside {@code border+blending} from the {@code interval} bounds.
	 *
	 * @param interval
	 * @param border
	 * @param blending
	 * @param transform
	 */
	public BlendingBlockSupplier(
			final Interval interval,
			final float[] border,
			final float[] blending,
			final AffineTransform3D transform)
	{
		this.blending = new Blending( interval, border, blending, transform );
	}

	/**
	 *
	 * @param srcPos
	 * 		min coordinate of the block to copy
	 * @param dest
	 *      {@code float[]} array to copy into.
	 * @param size
	 * 		the size of the block to copy
	 */
	@Override
	public void copy( final long[] srcPos, final Object dest, final int[] size )
	{
		final float[] weights = ( float[] ) dest;
		final long x0 = srcPos[ 0 ];
		final long y0 = srcPos[ 1 ];
		final long z0 = srcPos[ 2 ];
		final int sx = size[ 0 ];
		final int sy = size[ 1 ];
		final int sz = size[ 2 ];
		final double[] p = { x0, 0, 0 };
		for ( int z = 0; z < sz; ++z )
		{
			p[ 2 ] = z + z0;
			for ( int y = 0; y < sy; ++y )
			{
				p[ 1 ] = y + y0;
				final int offset = ( z * sy + y ) * sx;
				blending.fill_range( weights, offset, sx, p );
			}
		}
	}

	@Override
	public BlockSupplier< FloatType > threadSafe()
	{
		return this;
	}

	@Override
	public BlockSupplier< FloatType > independentCopy()
	{
		return this;
	}

	@Override
	public int numDimensions()
	{
		// TODO: do we need 2D ????
		return 3;
	}

	private static final FloatType type = new FloatType();

	@Override
	public FloatType getType()
	{
		return type;
	}
}
