package net.preibisch.mvrecon.process.fusion.transformed.nonrigid.grid;

import java.util.Collection;

import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.NonrigidIP;

public class VirtualGrid implements RandomAccessibleInterval< NumericAffineModel3D >
{
	final long[] dim, min, controlPointDistance;
	final int n;
	final double alpha;
	final Collection< ? extends NonrigidIP > ips;

	public VirtualGrid(
			final long[] dim,
			final long[] min,
			final long[] controlPointDistance,
			final double alpha,
			final Collection< ? extends NonrigidIP > ips )
	{
		this.dim = dim;
		this.min = min;
		this.controlPointDistance = controlPointDistance;
		this.n = dim.length;
		this.alpha = alpha;
		this.ips = ips;
	}

	@Override
	public RandomAccess< NumericAffineModel3D > randomAccess()
	{
		return new VirtualGridRandomAccess( min, controlPointDistance, alpha, ips, n );
	}

	@Override
	public RandomAccess< NumericAffineModel3D > randomAccess(
			Interval interval )
	{
		return randomAccess();
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public long min( final int d )
	{
		return 0;
	}

	@Override
	public void min( long[] min )
	{
		for ( int d = 0; d < n; ++d )
			min[ d ] = 0;
	}

	@Override
	public void min( Positionable min )
	{
		for ( int d = 0; d < n; ++d )
			min.setPosition( 0, d );
	}

	@Override
	public long max( final int d )
	{
		return dim[ d ] - 1;
	}

	@Override
	public void max( final long[] max )
	{
		for ( int d = 0; d < n; ++d )
			max[ d ] = dim[ d ] - 1;
	}

	@Override
	public void max( final Positionable max )
	{
		for ( int d = 0; d < n; ++d )
			max.setPosition( dim[ d ] - 1, d );
	}

	@Override
	public double realMin( final int d )
	{
		return min( d );
	}

	@Override
	public void realMin( final double[] min )
	{
		for ( int d = 0; d < n; ++d )
			min[ d ] = 0;
	}

	@Override
	public void realMin( final RealPositionable min )
	{
		for ( int d = 0; d < n; ++d )
			min.setPosition( 0, d );
	}

	@Override
	public double realMax( final int d )
	{
		return dim[ d ] - 1;
	}

	@Override
	public void realMax( final double[] max )
	{
		for ( int d = 0; d < n; ++d )
			max[ d ] = dim[ d ] - 1;
	}

	@Override
	public void realMax( final RealPositionable max )
	{
		for ( int d = 0; d < n; ++d )
			max.setPosition( dim[ d ] - 1, d );
	}

	@Override
	public void dimensions( final long[] dimensions )
	{
		for ( int d = 0; d < n; ++d )
			dimensions[ d ] = dim[ d ];
	}

	@Override
	public long dimension( final int d )
	{
		return dim[ d ];
	}
}
