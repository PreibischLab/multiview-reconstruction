package net.preibisch.mvrecon.process.deconvolution.util;

import java.util.List;

import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccess;

public class FusedNonZeroRandomAccess extends FusedRandomAccess
{
	final float[] max;
	final RealSum realSum;
	long countPixels;

	public FusedNonZeroRandomAccess(
			final int n,
			final List< ? extends RandomAccessible< FloatType > > images,
			final List< ? extends RandomAccessible< FloatType > > weights )
	{
		super( n, images, weights );

		this.max = new float[ images.size() ];
		for ( int i = 0; i < max.length; ++i )
			this.max[ i ] = 0;

		this.realSum = new RealSum();
		this.countPixels = 0;
	}

	public float[] getMax() { return max; }
	public RealSum getRealSum() { return realSum; }
	public long numContributingPixels() { return countPixels; }

	@Override
	public FloatType get()
	{
		double sumI = 0;
		double sumW = 0;

		double sum = 0;
		int count = 0;

		for ( int j = 0; j < numImages; ++j )
		{
			final double intensity = i[ j ].get().getRealDouble();

			if ( intensity > 0 )
			{
				final double weight = w[ j ].get().getRealDouble();
	
				sumI += intensity * weight;
				sumW += weight;

				max[ j ] = Math.max( max[ j ], (float)intensity );
				sum += intensity;
				++count;
			}
		}

		if ( sumW > 0 )
			value.set( (float)( sumI / sumW ) );
		else
			value.set(  0 );

		if ( count > 0 )
		{
			final double i = sum / count;
			realSum.add( i );
			++countPixels;
		}

		return value;
	}

}
