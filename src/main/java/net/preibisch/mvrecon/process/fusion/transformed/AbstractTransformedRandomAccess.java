package net.preibisch.mvrecon.process.fusion.transformed;

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

public abstract class AbstractTransformedRandomAccess< T extends RealType< T > > extends AbstractLocalizableInt implements RandomAccess< FloatType >
{
	final protected boolean hasMinValue;
	final protected float minValue;
	final protected FloatType outside;

	final protected RandomAccessibleInterval< T > img;
	final protected InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory;
	final protected RealRandomAccess< FloatType > ir;

	final protected int offsetX, offsetY, offsetZ;
	final protected int imgMinX, imgMinY, imgMinZ;
	final protected int imgMaxX, imgMaxY, imgMaxZ;

	final protected FloatType v;

	@SuppressWarnings("unchecked")
	public AbstractTransformedRandomAccess(
			final RandomAccessibleInterval< T > img, // from ImgLoader
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
		this.img = img;
		this.interpolatorFactory = interpolatorFactory;

		this.offsetX = (int)offset[ 0 ];
		this.offsetY = (int)offset[ 1 ];
		this.offsetZ = (int)offset[ 2 ];

		this.imgMinX = (int)img.min( 0 );
		this.imgMinY = (int)img.min( 1 );
		this.imgMinZ = (int)img.min( 2 );

		this.imgMaxX = (int)img.max( 0 );
		this.imgMaxY = (int)img.max( 1 );
		this.imgMaxZ = (int)img.max( 2 );

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
	}

	protected static final FloatType getInsideValue( final FloatType v, final RealRandomAccess< FloatType > ir, final boolean hasMinValue, final float minValue )
	{
		if ( hasMinValue )
		{
			// e.g. do not accept 0 values in the data where image data is present, 0 means no image data is available
			// (used in MVDeconvolution.computeQuotient)
			// return the minimal value of the lucy-richardson deconvolution = MVDeconvolution.minValue (e.g 0.0001)
			v.set( Math.max( minValue, ir.get().get() ) );

			return v;
		}
		else
		{
			return ir.get();
		}
	}

	protected static final boolean intersectsLinearInterpolation(
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
