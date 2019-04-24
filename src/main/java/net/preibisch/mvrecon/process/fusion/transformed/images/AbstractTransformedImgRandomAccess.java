package net.preibisch.mvrecon.process.fusion.transformed.images;

import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public abstract class AbstractTransformedImgRandomAccess< T extends RealType< T > > extends AbstractTransformedIntervalRandomAccess
{
	final protected boolean hasMinValue;
	final protected float minValue;

	final protected RandomAccessibleInterval< T > img;
	final protected RealRandomAccess< FloatType > ir;

	@SuppressWarnings("unchecked")
	public AbstractTransformedImgRandomAccess(
			final RandomAccessibleInterval< T > img, // from ImgLoader
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory,
			final boolean hasMinValue,
			final float minValue,
			final FloatType outside,
			final long[] offset )
	{
		super( img, interpolatorFactory, outside, offset );

		this.hasMinValue = hasMinValue;
		this.minValue = minValue;
		this.img = img;

		// extend input image and convert to floats
		final RandomAccessible< FloatType > input;

		if ( FloatType.class.isInstance( Views.iterable( img ).cursor().next() ) )
		{
			input = (RandomAccessible< FloatType >)img;
		}
		else
		{
			input =
				new ConvertedRandomAccessible< T, FloatType >(
						img,
						new RealFloatConverter< T >(),
						new FloatType() );
		}

		// make the interpolator
		this.ir = Views.interpolate( input, interpolatorFactory ).realRandomAccess();
	}

	protected static final FloatType getInsideValue( final FloatType v, final RealRandomAccess< FloatType > ir, final boolean hasMinValue, final float minValue )
	{
		if ( hasMinValue )
		{
			// e.g. do not accept 0 values in the data where image data is present, 0 means no image data is available
			// (used in MVDeconvolution.computeQuotient)
			// return the minimal value of the lucy-richardson deconvolution = MVDeconvolution.minValue (e.g 0.0001)
			v.set( Math.max( minValue, ir.get().get() ) );

			return v;
		}
		else
		{
			return ir.get();
		}
	}
}
