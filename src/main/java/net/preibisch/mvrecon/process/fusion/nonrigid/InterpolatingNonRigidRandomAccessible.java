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
package net.preibisch.mvrecon.process.fusion.nonrigid;

import java.util.Collection;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.process.fusion.nonrigid.grid.ModelGrid;

public class InterpolatingNonRigidRandomAccessible< T extends RealType< T > > implements RandomAccessible< FloatType >
{
	final RandomAccessibleInterval< T > img;
	final Collection< ? extends NonrigidIP > ips;
	final long[] boundingBoxOffset;
	final Interval boundingBox;

	final ModelGrid grid;
	final int n;

	final boolean hasMinValue;
	final float minValue;
	final FloatType outsideValue;

	InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory = new NLinearInterpolatorFactory< FloatType >();

	public InterpolatingNonRigidRandomAccessible(
		final RandomAccessibleInterval< T > img, // from ImgLoader
		final Collection< ? extends NonrigidIP > ips,
		final long[] controlPointDistance,
		final boolean hasMinValue,
		final float minValue,
		final FloatType outsideValue,
		final Interval boundingBox )
	{
		this.img = img;
		this.ips = ips;
		this.boundingBox = boundingBox;
		this.hasMinValue = hasMinValue;
		this.minValue = minValue;
		this.outsideValue = outsideValue;

		this.n = img.numDimensions();

		boundingBoxOffset = new long[ n ];

		for ( int d = 0; d < n; ++d )
			boundingBoxOffset[ d ] = boundingBox.min( d );

		this.grid = new ModelGrid( controlPointDistance, boundingBox, ips );

		/*
		final RealRandomAccess< NumericAffineModel3D > model = this.grid.realRandomAccess();
		model.setPosition( new long[] { boundingBox.min( 0 ), boundingBox.min( 1 ), boundingBox.min( 2 ) } );
		System.out.println( model.get().getModel() );

		model.setPosition( new long[] { boundingBox.min( 0 ) + 1, boundingBox.min( 1 ), boundingBox.min( 2 ) } );
		System.out.println( model.get().getModel() );

		SimpleMultiThreading.threadHaltUnClean(); */
	}

	public InterpolatingNonRigidRandomAccessible(
			final RandomAccessibleInterval< T > img, // from ImgLoader
			final Collection< ? extends NonrigidIP > ips,
			final long[] controlPointDistance,
			final Interval boundingBox )
	{
		this( img, ips, controlPointDistance, false, 0.0f, new FloatType( 0 ), boundingBox );
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
		return new InterpolationgNonRigidRandomAccess< T >( img, grid, interpolatorFactory, hasMinValue, minValue, outsideValue, boundingBoxOffset );
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
