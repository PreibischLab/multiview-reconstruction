package net.preibisch.mvrecon.process.fusion.intensity.mpicbg;

import java.util.Arrays;

public class SimpleErrorStatistic
{
	private final double[] values;

	private int size = 0;

	private double mean = 0;

	public SimpleErrorStatistic( final int capacity )
	{
		values = new double[ capacity ];
	}

	public double getMedian()
	{
		Arrays.sort( values, 0, size );
		final int m = size / 2;
		if ( size % 2 == 0 )
			return ( values[ m - 1 ] + values[ m ] ) / 2.0;
		else
			return values[ m ];
	}

	final public void add( final double new_value )
	{
		int i = size++;
		values[ i ] = new_value;

		final double delta = new_value - mean;
		mean += delta / size;
	}

	public int n()
	{
		return size;
	}

	public double mean()
	{
		return mean;
	}

	public void clear()
	{
		size = 0;
		mean = 0;
	}
}
