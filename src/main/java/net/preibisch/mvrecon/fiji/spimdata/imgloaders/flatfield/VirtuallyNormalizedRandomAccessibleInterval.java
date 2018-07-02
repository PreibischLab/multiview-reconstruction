package net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield;

import net.imglib2.AbstractInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

public class VirtuallyNormalizedRandomAccessibleInterval <T extends RealType< T >> extends AbstractInterval implements RandomAccessibleInterval< T >
{

	private final double minValue;
	private final double maxValue;
	private final RandomAccessibleInterval< T > input;
	
	public VirtuallyNormalizedRandomAccessibleInterval(RandomAccessibleInterval< T > input)
	{
		super( input );
		this.input = input;
		
		// TODO: slow, do MT?
		final Pair< Double, Double > minMax = FlatFieldCorrectedRandomAccessibleInterval.getMinMax( input );
		minValue = minMax.getA();
		maxValue = minMax.getB();
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new VirtuallyNormalizedRandomAccess();
	}

	@Override
	public RandomAccess< T > randomAccess(Interval interval)
	{
		return randomAccess();
	}
	
	private class VirtuallyNormalizedRandomAccess extends Point implements RandomAccess< T >
	{
		private final T value;
		private final RandomAccess< T > inputRA;

		public VirtuallyNormalizedRandomAccess()
		{
			super(input.numDimensions());
			this.value = Views.iterable( input ).firstElement().createVariable();
			inputRA = input.randomAccess();
		}
		
		@Override
		public T get()
		{
			inputRA.setPosition( this );			
			double inValue = inputRA.get().getRealDouble();

			value.setReal( (inValue - minValue ) / ( maxValue - minValue ) );			
			return value;
		}

		@Override
		public Sampler< T > copy()
		{
			return copyRandomAccess();
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			return new VirtuallyNormalizedRandomAccess();
		}
		
	}
	

}
