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

import java.util.ArrayList;
import java.util.Collection;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.MovingLeastSquaresTransform;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Virtually transforms any RandomAccessibleInterval&lt;RealType&gt; into a RandomAccess&lt;FloatType&gt; using an AffineTransformation
 * and Linear Interpolation. It will only interpolate from the actual data (no outofbounds) to avoid artifacts at the edges
 * and return 0 outside by default (can be changed).
 * 
 * @author preibisch
 */
public class NonRigidRandomAccess< T extends RealType< T > > extends AbstractLocalizableInt implements RandomAccess< FloatType >
{
	final boolean hasMinValue;
	final float minValue;
	final FloatType outside;

	final RandomAccessibleInterval< T > img;
	final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory;
	final Collection< ? extends NonrigidIP > ips;
	final long[] offset;
	final double[] s;
	final FloatType v;

	final MovingLeastSquaresTransform transform;

	final RealRandomAccess< FloatType > ir;
	final int offsetX, offsetY, offsetZ;
	final int imgMinX, imgMinY, imgMinZ;
	final int imgMaxX, imgMaxY, imgMaxZ;

	@SuppressWarnings("unchecked")
	public NonRigidRandomAccess(
			final RandomAccessibleInterval< T > img, // from ImgLoader
			final Collection< ? extends NonrigidIP > ips,
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory,
			final boolean hasMinValue,
			final float minValue,
			final FloatType outside,
			final long[] offset )
	{
		super( img.numDimensions() );

		this.outside = outside;

		this.hasMinValue = hasMinValue;
		this.minValue = minValue;

		this.offsetX = (int)offset[ 0 ];
		this.offsetY = (int)offset[ 1 ];
		this.offsetZ = (int)offset[ 2 ];

		this.imgMinX = (int)img.min( 0 );
		this.imgMinY = (int)img.min( 1 );
		this.imgMinZ = (int)img.min( 2 );

		this.imgMaxX = (int)img.max( 0 );
		this.imgMaxY = (int)img.max( 1 );
		this.imgMaxZ = (int)img.max( 2 );

		this.img = img;
		this.interpolatorFactory = interpolatorFactory;
		this.ips = ips;
		this.offset = offset;
		this.s = new double[ n ];
		this.v = new FloatType();

		// extend input image and convert to floats
		final RandomAccessible< FloatType > input;

		if ( FloatType.class.isInstance( Views.iterable( img ).cursor().next() ) )
		{
			input = (RandomAccessible< FloatType >)img;
		}
		else
		{
			input =
				new ConvertedRandomAccessible< T, FloatType >(
						img,
						new RealFloatConverter< T >(),
						new FloatType() );
		}

		// make the interpolator
		this.ir = Views.interpolate( input, interpolatorFactory ).realRandomAccess();

		//MovingLeastSquaresMesh< ? > mlsm;
		//MovingLeastSquaresTransform2 t2;

		this.transform = new MovingLeastSquaresTransform();
		final ArrayList< PointMatch > matches = new ArrayList<>();

		for ( final NonrigidIP ip : ips )
			matches.add( new PointMatch( new Point( ip.getL().clone() ), new Point( ip.getTargetW().clone() ) ) );

		IOFunctions.println( matches.size() );
		try
		{
			transform.setMatches( matches );
		} catch ( NotEnoughDataPointsException | IllDefinedDataPointsException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public FloatType get()
	{
		// go from PSI(Decon)_image local coordinate system to world coordinate system
		s[ 0 ] = position[ 0 ] + offsetX;
		s[ 1 ] = position[ 1 ] + offsetY;
		s[ 2 ] = position[ 2 ] + offsetZ;

		// go from world coordinate system to local coordinate system of input image (pixel coordinates)
		//transform.applyInverse( t, s );
		transform.applyInPlace( s );

		// check if position t is inside of the input image (pixel coordinates)
		if ( intersectsLinearInterpolation( s[ 0 ], s[ 1 ], s[ 2 ], imgMinX, imgMinY, imgMinZ, imgMaxX, imgMaxY, imgMaxZ ) )
		{
			ir.setPosition( s );

			return getInsideValue( v, ir, hasMinValue, minValue );
		}
		else
		{
			return outside;
		}
	}

	private static final FloatType getInsideValue( final FloatType v, final RealRandomAccess< FloatType > ir, final boolean hasMinValue, final float minValue )
	{
		if ( hasMinValue )
		{
			// do not accept 0 values in the data where image data is present, 0 means no image data is available
			// (used in MVDeconvolution.computeQuotient)
			// here return the minimal value of the lucy-richardson deconvolution = MVDeconvolution.minValue (e.g 0.0001)
			v.set( Math.max( minValue, ir.get().get() ) );

			return v;
		}
		else
		{
			return ir.get();
		}
	}

	private static final boolean intersectsLinearInterpolation(
			final double x, final double y, final double z,
			final long minX, final long minY, final long minZ,
			final long maxX, final long maxY, final long maxZ )
	{
		// to avoid interpolation artifacts from the outofboundsstrategy,
		// the coordinate has to be bigger than min and smaller than max (assuming linear or NN interpolation)
		if ( x > minX && y > minY && z > minZ && x < maxX && y < maxY && z < maxZ )
			return true;
		else
			return false;
	}

	@Override
	public NonRigidRandomAccess< T > copy()
	{
		return copyRandomAccess();
	}

	@Override
	public NonRigidRandomAccess< T > copyRandomAccess()
	{
		final NonRigidRandomAccess< T > r = new NonRigidRandomAccess< T >( img, ips, interpolatorFactory, hasMinValue, minValue, outside, offset );
		r.setPosition( this );
		return r;
	}

	@Override
	public void fwd( final int d ) { ++position[ d ]; }

	@Override
	public void bck( final int d ) { --position[ d ]; }

	@Override
	public void move( final int distance, final int d ) { position[ d ] += distance; }

	@Override
	public void move( final long distance, final int d ) { position[ d ] += distance; }

	@Override
	public void move( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += localizable.getIntPosition( d );
	}

	@Override
	public void move( final int[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];
	}

	@Override
	public void move( final long[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		localizable.localize( position );
	}

	@Override
	public void setPosition( final int[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = pos[ d ];
	}

	@Override
	public void setPosition( final long[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = ( int ) pos[ d ];
	}

	@Override
	public void setPosition( final int pos, final int d )
	{
		position[ d ] = pos;
	}

	@Override
	public void setPosition( final long pos, final int d )
	{
		position[ d ] = ( int ) pos;
	}
}
