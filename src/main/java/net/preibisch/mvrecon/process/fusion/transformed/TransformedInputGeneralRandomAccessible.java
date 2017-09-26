package net.preibisch.mvrecon.process.fusion.transformed;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class TransformedInputGeneralRandomAccessible< T extends RealType< T > > implements RandomAccessible< FloatType >
{
	final RandomAccessibleInterval< T > img;
	final AffineTransform3D transform;
	final long[] offset;

	final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory;
	final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory;

	public TransformedInputGeneralRandomAccessible(
		final RandomAccessibleInterval< T > img, // from ImgLoader
		final AffineTransform3D transform,
		final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory,
		final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory,
		final long[] offset )
	{
		this.img = img;
		this.interpolatorFactory = interpolatorFactory;
		this.outOfBoundsFactory = outOfBoundsFactory;
		this.transform = transform;
		this.offset = offset;
	}

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		return new TransformedInputGeneralRandomAccess< T >( img, transform, interpolatorFactory, outOfBoundsFactory, offset );
	}

	@Override
	public RandomAccess< FloatType > randomAccess( final Interval arg0 )
	{
		return randomAccess();
	}

	@Override
	public int numDimensions()
	{
		return img.numDimensions();
	}
}
