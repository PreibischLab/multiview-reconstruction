package spim.fiji.spimdata.imgloaders.flatfield;

import net.imglib2.AbstractInterval;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

/*
 * 
 */
public class FlatFieldCorrectedRandomAccessibleInterval <O extends RealType< O >, T extends RealType<T>, S extends RealType<S>, R extends RealType<R>> extends AbstractInterval implements RandomAccessibleInterval< O >
{
	private final RandomAccessibleInterval< T > sourceImg;
	private final RandomAccessibleInterval< S > brightImg;
	private final RandomAccessibleInterval< R > darkImg;
	private final double meanBrightCorrected;
	private final O type;

	/*
	 * TODO: add option to not drop last dimension (for 2D input)
	 */
	
	public FlatFieldCorrectedRandomAccessibleInterval(O outputType, RandomAccessibleInterval< T > sourceImg, RandomAccessibleInterval< S > brightImg, RandomAccessibleInterval< R > darkImg)
	{
		super( sourceImg );
		this.sourceImg = sourceImg;
		this.brightImg = brightImg;
		this.darkImg = darkImg;

		meanBrightCorrected = getMeanCorrected( brightImg, darkImg );
		type = outputType;
	}

	@Override
	public RandomAccess< O > randomAccess()
	{
		return new FlatFieldCorrectedRandomAccess();
	}

	@Override
	public RandomAccess< O > randomAccess(Interval interval)
	{
		return randomAccess();
	}
	
	private class FlatFieldCorrectedRandomAccess extends Point implements RandomAccess< O >
	{
		/*
		 * TODO: manually implement move methods
		 */
		
		private final RandomAccess< T > sourceRA;
		private final RandomAccess< S > brightRA;
		private final RandomAccess< R > darkRA;
		private final O value;
		
		public FlatFieldCorrectedRandomAccess()
		{
			super( sourceImg.numDimensions() );
			sourceRA = sourceImg.randomAccess();
			brightRA = brightImg.randomAccess();
			darkRA = darkImg.randomAccess();
			value = type.createVariable();
		}

		@Override
		public O get()
		{
			// NB: the flat field images seem to be 3D with 1 z slice
			// if they were truly 2D, we would use position.length - 1
			final long[] positionNMinus1D = new long[ position.length ];
			// only copy position of n-1 dimensions
			System.arraycopy( position, 0, positionNMinus1D, 0, position.length - 1 );

			sourceRA.setPosition( position );
			brightRA.setPosition( positionNMinus1D );
			darkRA.setPosition( positionNMinus1D );

			final double corrBright = brightRA.get().getRealDouble() - darkRA.get().getRealDouble();
			final double corrImg = sourceRA.get().getRealDouble() - darkRA.get().getRealDouble();

			if (corrBright == 0)
				value.setReal( 0.0 );
			else
			{
				final double corr = Math.min( Math.max( corrImg * meanBrightCorrected / corrBright, value.getMinValue() ), value.getMaxValue() );
				value.setReal( corr );
			}

			return value;
		}

		@Override
		public Sampler< O > copy()
		{
			return copyRandomAccess();
		}

		@Override
		public RandomAccess< O > copyRandomAccess()
		{
			final FlatFieldCorrectedRandomAccessibleInterval<O, T, S, R >.FlatFieldCorrectedRandomAccess copy = new FlatFieldCorrectedRandomAccess();
			copy.setPosition( this );
			return copy;
		}
		
	}
	
	public static <P extends RealType< P >, Q extends RealType< Q >> double getMeanCorrected(RandomAccessibleInterval< P > brightImg, RandomAccessibleInterval< Q > darkImg)
	{
		final RealSum sum = new RealSum();
		long count = 0;
		
		final Cursor< P > brightCursor = Views.iterable( brightImg ).cursor();
		final RandomAccess< Q > darkRA = darkImg.randomAccess();
		
		while (brightCursor.hasNext())
		{
			brightCursor.fwd();
			darkRA.setPosition( brightCursor );
			sum.add( brightCursor.get().getRealDouble() - darkRA.get().getRealDouble());
			count++;
		}
		
		if (count == 0)
			return 0.0;
		else
			return sum.getSum() / count;
		
	}
	
	
	public static <P extends RealType< P >> Pair<Double, Double> getMinMax(RandomAccessibleInterval< P > img)
	{
		double min = Double.MAX_VALUE;
		double max = - Double.MAX_VALUE;

		for (final P pixel : Views.iterable( img ))
		{
			double value = pixel.getRealDouble();
			
			if (value > max)
				max = value;
			
			if (value < min)
				min = value;
		}
		
		return new ValuePair< Double, Double >( min, max );
	}

}
