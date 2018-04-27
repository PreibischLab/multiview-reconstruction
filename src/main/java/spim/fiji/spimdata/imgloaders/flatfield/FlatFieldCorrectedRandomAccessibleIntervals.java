package spim.fiji.spimdata.imgloaders.flatfield;

import bdv.util.ConstantRandomAccessible;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class FlatFieldCorrectedRandomAccessibleIntervals
{
	public static < R extends RealType< R >, S extends RealType< S >, T extends RealType< T >> RandomAccessibleInterval< R > create(
			RandomAccessibleInterval< R > sourceImg,
			RandomAccessibleInterval< S > brightImg,
			RandomAccessibleInterval< T > darkImg )
	{
		R type = Views.iterable( sourceImg ).firstElement().createVariable();
		return create( sourceImg, brightImg, darkImg, type );
	}
	public static <O extends RealType< O >, R extends RealType< R >, S extends RealType< S >, T extends RealType< T >> RandomAccessibleInterval< O > create(
			RandomAccessibleInterval< R > sourceImg,
			RandomAccessibleInterval< S > brightImg,
			RandomAccessibleInterval< T > darkImg,
			O outputType)
	{

		final long[] minsNMinus1D = new long[sourceImg.numDimensions()];
		final long[] maxsNMinus1D = new long[sourceImg.numDimensions()];

		for (int d = 0; d < sourceImg.numDimensions() - 1; ++d)
		{
			minsNMinus1D[d] = sourceImg.min( d );
			maxsNMinus1D[d] = sourceImg.max( d );
		}
		maxsNMinus1D[sourceImg.numDimensions() - 1] = 1;

		final FinalInterval intervalNMinus1D = new FinalInterval( minsNMinus1D, maxsNMinus1D );

		if (brightImg == null && darkImg == null)
		{
			// assume bright and dark images constant -> should return original
			// TODO: 'optimize' by really returning sourceImg?
			final ConstantRandomAccessible< FloatType > constantBright = new ConstantRandomAccessible<FloatType>( new FloatType(1.0f), sourceImg.numDimensions() );
			final ConstantRandomAccessible< FloatType > constantDark = new ConstantRandomAccessible<FloatType>( new FloatType(0.0f), sourceImg.numDimensions() );
			return new FlatFieldCorrectedRandomAccessibleInterval<>(outputType, sourceImg, Views.interval( constantBright, intervalNMinus1D ), Views.interval( constantDark, intervalNMinus1D ) );
		}
		else if (brightImg == null)
		{
			// assume bright image == constant
			final ConstantRandomAccessible< FloatType > constantBright = new ConstantRandomAccessible<FloatType>( new FloatType(1.0f), sourceImg.numDimensions() );
			return new FlatFieldCorrectedRandomAccessibleInterval<>(outputType, sourceImg, Views.interval( constantBright, intervalNMinus1D ), Views.interval( Views.extendBorder(Views.addDimension( darkImg, 0, 1 ) ), intervalNMinus1D ) );
		}
		else if (darkImg == null)
		{
			// assume dark image == constant == 0;
			final ConstantRandomAccessible< FloatType > constantDark = new ConstantRandomAccessible<FloatType>( new FloatType(0.0f), sourceImg.numDimensions() );
			return new FlatFieldCorrectedRandomAccessibleInterval<>(outputType, sourceImg, Views.interval( Views.extendBorder( Views.addDimension( brightImg, 0, 1 ) ), intervalNMinus1D ), Views.interval( constantDark, intervalNMinus1D ) );
		}
			
		return new FlatFieldCorrectedRandomAccessibleInterval<>(outputType, sourceImg, Views.interval( Views.extendBorder( Views.addDimension( brightImg, 0, 1 ) ), intervalNMinus1D ), Views.interval( Views.extendBorder( Views.addDimension( darkImg, 0, 1 ) ), intervalNMinus1D ) );
	}
}
