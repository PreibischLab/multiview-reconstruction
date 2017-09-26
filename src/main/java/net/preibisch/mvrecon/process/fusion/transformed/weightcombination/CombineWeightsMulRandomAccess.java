package net.preibisch.mvrecon.process.fusion.transformed.weightcombination;

import java.util.List;

import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.real.FloatType;

public class CombineWeightsMulRandomAccess extends CombineWeightsRandomAccess
{
	public CombineWeightsMulRandomAccess(
			final int n,
			final List< ? extends RandomAccessible< FloatType > > weights )
	{
		super( n, weights );
	}

	@Override
	public FloatType get()
	{
		double mulW = 1;

		for ( int j = 0; j < numImages; ++j )
			mulW *= w[ j ].get().getRealDouble();

		value.set( (float)mulW );

		return value;
	}

	@Override
	public CombineWeightsMulRandomAccess copyRandomAccess()
	{
		final CombineWeightsMulRandomAccess r = new CombineWeightsMulRandomAccess( n, weights );
		r.setPosition( this );
		return r;
	}

}
