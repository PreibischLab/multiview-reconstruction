package net.preibisch.mvrecon.process.fusion.nonrigid.grid;

import mpicbg.models.AffineModel3D;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.Sampler;
import net.imglib2.img.list.ListImg;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class ModelGridAccess implements RealRandomAccess< NumericAffineModel3D >
{
	final double[] l, tmp;
	final int n;
	final RealRandomAccess< NumericAffineModel3D > interpolated;
	final ListImg< NumericAffineModel3D > grid;
	final long[] min, controlPointDistance;
	final NumericAffineModel3D model;

	public ModelGridAccess(
			final ListImg< NumericAffineModel3D > grid,
			final long[] min,
			final long[] controlPointDistance)
	{
		this.n = grid.numDimensions();
		this.l = new double[ n ];
		this.tmp = new double[ n ];

		this.min = min;
		this.controlPointDistance = controlPointDistance;

		this.grid = grid;
		this.model = new NumericAffineModel3D( new AffineModel3D() );
		this.interpolated = Views.interpolate( grid, new NLinearInterpolatorFactory<>() ).realRandomAccess();
	}

	@Override
	public NumericAffineModel3D get()
	{
		System.out.print( Util.printCoordinates( l ) );

		getLocalCoordinates( tmp, l, min, controlPointDistance, n );

		System.out.println( " >>> " + Util.printCoordinates( tmp ) );

		interpolated.setPosition( tmp );

		return interpolated.get();
	}

	protected static final void getLocalCoordinates( final double[] pos, final double[] screenCoordinate, final long[] min, final long[] controlPointDistance, final int n )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = ( screenCoordinate[ d ] - min[ d ] ) / (double)controlPointDistance[ d ];
	}

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = (float)l[ d ];
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = l[ d ];
	}

	@Override
	public float getFloatPosition( final int d ){ return (float)l[ d ]; }

	@Override
	public double getDoublePosition( final int d ) { return l[ d ]; }

	@Override
	public int numDimensions() { return n; }

	@Override
	public void move( final float distance, final int d ) { l[ d ] += distance; }

	@Override
	public void move( final double distance, final int d ) { l[ d ] += distance; }

	@Override
	public void move( final RealLocalizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] += localizable.getFloatPosition( d );
	}

	@Override
	public void move( final float[] distance )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] += distance[ d ];
	}

	@Override
	public void move( final double[] distance )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] += distance[ d ];
	}

	@Override
	public void setPosition( final RealLocalizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] = localizable.getFloatPosition( d );
	}

	@Override
	public void setPosition( final float[] position )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final double[] position )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] =(float)position[ d ];
	}

	@Override
	public void setPosition( final float position, final int d ) { l[ d ] = position; }

	@Override
	public void setPosition( final double position, final int d ) { l[ d ] = (float)position; }

	@Override
	public void fwd( final int d ) { ++l[ d ]; }

	@Override
	public void bck( final int d ) { --l[ d ]; }

	@Override
	public void move( final int distance, final int d ) { l[ d ] += distance; }

	@Override
	public void move( final long distance, final int d ) { l[ d ] += distance; }

	@Override
	public void move( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] += localizable.getFloatPosition( d );
	}

	@Override
	public void move( final int[] distance )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] += distance[ d ];
	}

	@Override
	public void move( final long[] distance )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] += distance[ d ];
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] = localizable.getFloatPosition( d );
	}

	@Override
	public void setPosition( final int[] position )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final long[] position )
	{
		for ( int d = 0; d < n; ++d )
			l[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final int position, final int d ) { l[ d ] = position; }

	@Override
	public void setPosition( final long position, final int d ) { l[ d ] = position; }

	@Override
	public Sampler< NumericAffineModel3D > copy()
	{
		return copyRealRandomAccess();
	}

	@Override
	public RealRandomAccess< NumericAffineModel3D > copyRealRandomAccess()
	{
		return new ModelGridAccess( grid, min, controlPointDistance );
	}
}
