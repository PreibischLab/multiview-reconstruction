/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.fusion.transformed;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class TransformedInputRandomAccessible< T extends RealType< T > > implements RandomAccessible< FloatType >
{
	final RandomAccessibleInterval< T > img;
	final AffineTransform3D transform;
	final long[] offset;

	final boolean hasMinValue;
	final float minValue;
	final FloatType outside;

	final boolean is2d;

	InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory = new NLinearInterpolatorFactory< FloatType >();

	public TransformedInputRandomAccessible(
		final RandomAccessibleInterval< T > img, // from ImgLoader
		final AffineTransform3D transform,
		final boolean hasMinValue,
		final float minValue,
		final FloatType outside,
		final long[] offset )
	{
		this.img = img;
		this.transform = transform;
		this.offset = offset;
		this.hasMinValue = hasMinValue;
		this.minValue = minValue;
		this.outside = outside;

		if ( img.min( 2 ) == 0 && img.max( 2 ) == 0 && offset[ 2 ] == 0)
			is2d = true;
		else
			is2d = false;
	}

	public TransformedInputRandomAccessible(
			final RandomAccessibleInterval< T > img, // from ImgLoader
			final AffineTransform3D transform,
			final long[] offset )
	{
		this( img, transform, false, 0.0f, new FloatType( 0 ), offset );
	}

	public void setLinearInterpolation()
	{
		this.interpolatorFactory = new NLinearInterpolatorFactory< FloatType >();
	}

	public void setNearestNeighborInterpolation()
	{
		this.interpolatorFactory = new NearestNeighborInterpolatorFactory< FloatType >();
	}

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		if ( is2d )
			return new TransformedInputRandomAccess2d< T >( img, transform, interpolatorFactory, hasMinValue, minValue, outside, offset );
		else
			return new TransformedInputRandomAccess< T >( img, transform, interpolatorFactory, hasMinValue, minValue, outside, offset );
	}

	@Override
	public RandomAccess< FloatType > randomAccess( final Interval arg0 )
	{
		return randomAccess();
	}

	@Override
	public int numDimensions()
	{
		return img.numDimensions();
	}
}
