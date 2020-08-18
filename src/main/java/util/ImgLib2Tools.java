package util;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class ImgLib2Tools
{
	public static final < T extends RealType< T > > RandomAccessibleInterval<FloatType> convertVirtual( final RandomAccessibleInterval< T > img )
	{
		return new ConvertedRandomAccessibleInterval<T, FloatType>(
				img,
				new RealFloatConverter<T>(),
				new FloatType() );
	}

	public static final < T extends RealType< T > > RandomAccessibleInterval<FloatType> normalizeVirtual( final RandomAccessibleInterval< T > img, final double min, final double max )
	{
		final float minf = (float)min;
		final float maxf = (float)max;
		
		return new ConvertedRandomAccessibleInterval<T, FloatType>(
				img,
				new Converter<T, FloatType>()
				{
					@Override
					public void convert( final T input, final FloatType output)
					{
						output.set( ( input.getRealFloat() - minf ) / ( maxf - minf ) );
					}
				},
				new FloatType() );
	}

	public static final < T extends RealType< T > > RandomAccessibleInterval<FloatType> normalizeVirtual( final RandomAccessibleInterval< T > img )
	{
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( final T t : Views.flatIterable( img ) )
		{
			final double v = t.getRealDouble();

			if ( v < min )
				min = v;

			if ( v > max )
				max = v;
		}

		return normalizeVirtual( img, min, max );
	}
}
