package net.preibisch.mvrecon.process.deconvolution.util;

import java.util.List;
import java.util.Vector;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;

public class FusedNonZeroRandomAccessibleInterval extends FusedRandomAccessibleInterval
{
	final Vector< FusedNonZeroRandomAccess > accesses;

	public FusedNonZeroRandomAccessibleInterval(
			final Interval interval,
			final List< ? extends RandomAccessible< FloatType > > images,
			final List< ? extends RandomAccessible< FloatType > > weights )
	{
		super( interval, images, weights );

		this.accesses = new Vector<>();
	}

	public Vector< FusedNonZeroRandomAccess > getAllAccesses() { return accesses; }

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		final FusedNonZeroRandomAccess r = new FusedNonZeroRandomAccess( numDimensions(), getImages(), getWeights() );

		accesses.add( r );

		return r;
	}
}
