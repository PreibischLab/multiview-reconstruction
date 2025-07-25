/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.fusion.intensity.mpicbg;

import java.util.Arrays;

class SimpleErrorStatistic
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
