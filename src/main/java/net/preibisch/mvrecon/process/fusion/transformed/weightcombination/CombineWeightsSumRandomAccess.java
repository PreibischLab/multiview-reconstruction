package net.preibisch.mvrecon.process.fusion.transformed.weightcombination;

import java.util.List;

import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.real.FloatType;

public class CombineWeightsSumRandomAccess extends CombineWeightsRandomAccess
{
	public CombineWeightsSumRandomAccess(
			final int n,
			final List< ? extends RandomAccessible< FloatType > > weights )
	{
		super( n, weights );
	}

	@Override
	public FloatType get()
	{
		double sumW = 0;

		for ( int j = 0; j < numImages; ++j )
			sumW += w[ j ].get().getRealDouble();

		value.set( (float)sumW );

		return value;
	}

	@Override
	public CombineWeightsSumRandomAccess copyRandomAccess()
	{
		final CombineWeightsSumRandomAccess r = new CombineWeightsSumRandomAccess( n, weights );
		r.setPosition( this );
		return r;
	}

}
