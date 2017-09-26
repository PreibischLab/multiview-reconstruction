package net.preibisch.mvrecon.process.deconvolution.iteration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.deconvolution.DeconView;

public class PsiInitializationAvgApprox implements PsiInitialization
{
	double avg = -1;
	float[] max = null;

	@Override
	public boolean runInitialization( final Img< FloatType > psi, final List< DeconView > views, final ExecutorService service )
	{
		this.max = new float[ views.size() ];

		// split up into many parts for multithreading
		final ArrayList< Callable< Pair< double[], Integer > > > tasks = new ArrayList<>();

		for ( int i = 0; i < views.size(); ++i )
			tasks.add( new PsiInitializationAvgApproxThread( views.get( i ), i ) );

		double avg = 0;

		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< Pair< double[], Integer > > > imgIntensities = service.invokeAll( tasks );

			for ( final Future< Pair< double[], Integer > > future : imgIntensities )
			{
				final Pair< double[], Integer > f = future.get();
				final double[] minMaxAvg = f.getA();

				avg += minMaxAvg[ 2 ];
				max[ f.getB() ] = (float)minMaxAvg[ 1 ];
			}
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to fuse initial iteration: " + e );
			e.printStackTrace();
			return false;
		}

		avg /= (double)max.length;

		if ( Double.isNaN( avg ) )
		{
			avg = 1.0;
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): ERROR! Computing average FAILED, is NaN, setting it to: " + avg );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Average intensity in overlapping area: " + avg );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting image to average intensity: " + avg );

		for ( final FloatType t : psi )
			t.set( (float)avg );

		return true;
	}

	@Override
	public double getAvg() { return avg; }

	@Override
	public float[] getMax() { return max; }
}
