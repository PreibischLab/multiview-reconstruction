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
package net.preibisch.mvrecon.process.fusion.transformed.nonrigid;

import java.util.Collection;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.process.fusion.transformed.AbstractTransformedImgRandomAccessible;

public class NonRigidRandomAccessible< T extends RealType< T > > extends AbstractTransformedImgRandomAccessible< T >
{
	final Collection< ? extends NonrigidIP > ips;

	public NonRigidRandomAccessible(
		final RandomAccessibleInterval< T > img, // from ImgLoader
		final Collection< ? extends NonrigidIP > ips,
		final boolean hasMinValue,
		final float minValue,
		final FloatType outsideValue,
		final Interval boundingBox )
	{
		super( img, hasMinValue, minValue, outsideValue, boundingBox );

		this.ips = ips;
	}

	public NonRigidRandomAccessible(
			final RandomAccessibleInterval< T > img, // from ImgLoader
			final Collection< ? extends NonrigidIP > ips,
			final Interval boundingBox )
	{
		this( img, ips, false, 0.0f, new FloatType( 0 ), boundingBox );
	}

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		return new NonRigidRandomAccess< T >( img, ips, interpolatorFactory, hasMinValue, minValue, outsideValue, boundingBoxOffset );
	}
}
