package net.preibisch.mvrecon.process.deconvolution.iteration;

import java.util.Random;
import java.util.concurrent.Callable;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.deconvolution.DeconView;

public class PsiInitializationAvgApproxThread implements Callable< Pair< double[], Integer > >
{
	final DeconView mvdecon;
	final int listId;
	final int numPixels;
	final Random rnd;

	public PsiInitializationAvgApproxThread( final DeconView mvdecon, final int listId, final int numPixels )
	{
		this.mvdecon = mvdecon;
		this.listId = listId;
		this.numPixels = numPixels;
		this.rnd = new Random( 34556 + listId ); // not the same pseudo-random numbers for each thread
	}

	public PsiInitializationAvgApproxThread( final DeconView mvdecon, final int listId )
	{
		this( mvdecon, listId, 1000 );
	}

	@Override
	public Pair< double[], Integer > call() throws Exception
	{
		final RandomAccessibleInterval< FloatType > img = mvdecon.getImage();

		// run threads and combine results
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		final RealSum realSum = new RealSum( numPixels );

		long numPixels = 0;

		for ( int d = 0; d < img.numDimensions(); ++d )
		{
			final IterableInterval< FloatType > iterable = Views.iterable( Views.hyperSlice( img, 0, img.dimension( 0 ) / 2 ) );
			numPixels += iterable.size();

			for ( final FloatType t : iterable )
			{
				final double v = t.getRealDouble();

				min = Math.min( min, v );
				max = Math.max( max, v );
				realSum.add( v );
			}
		}

		return new ValuePair<>( new double[]{ min, max, realSum.getSum() / (double)numPixels }, listId );
	}
}
