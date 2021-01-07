/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineSet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;

public class TransformVirtual
{
	/**
	 * Scale the affine transform (use with scaleBoundingBox so it is the right image, but just smaller)
	 * 
	 * @param t transform
	 * @param factor scaling factor
	 */
	public static void scaleTransform( final AffineTransform3D t, final double factor )
	{
		final AffineTransform3D at = new AffineTransform3D();
		at.scale( factor );
		t.preConcatenate( at );
	}
	
	public static void scaleTransform( final AffineTransform3D t, final double[] factors )
	{
		final AffineTransform at = new AffineTransform(t.numDimensions());
		for (int d = 0; d < at.numDimensions(); d++)
			at.set( factors[d], d, d );
			
		t.preConcatenate( at );
	}

	/**
	 * Scale the bounding box (use with scaleTransform so it is the right image, but just smaller)
	 * 
	 * @param boundingBox the bounding box
	 * @param factor scaling factor
	 * @return scaled bounding box
	 */
	public static Interval scaleBoundingBox( final Interval boundingBox, final double factor )
	{
		return scaleBoundingBox( boundingBox, factor, null );
	}

	public static Interval scaleBoundingBox( final Interval boundingBox, final double factor, final double[] offset )
	{
		final int n = boundingBox.numDimensions();
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];

		for ( int d = 0; d < min.length; ++ d )
		{
			final double minValue = boundingBox.min( d ) * factor;

			min[ d ] = Math.round( minValue );
			max[ d ] = Math.round( boundingBox.max( d ) * factor );

			if ( offset != null )
				offset[ d ] = minValue - min[ d ];
		}

		return new FinalInterval( min, max );
	}
	
	
	public static Interval scaleBoundingBox( final Interval boundingBox, final double[] factors )
	{
		final int n = boundingBox.numDimensions();
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];

		for ( int d = 0; d < min.length; ++ d )
		{
			min[ d ] = Math.round( boundingBox.min( d ) * factors[d] );
			max[ d ] = Math.round( boundingBox.max( d ) * factors[d] );
		}

		return new FinalInterval( min, max );
	}
	
}
