package net.preibisch.mvrecon.process.fusion.transformed.nonrigid.grid;

import java.util.ArrayList;
import java.util.Collection;

import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.MovingLeastSquaresTransform2;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.Sampler;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.NonrigidIP;

public class VirtualGridRandomAccess extends AbstractLocalizableInt implements RandomAccess< NumericAffineModel3D >
{
	final double[] pos = new double[ n ];
	final long[] min, controlPointDistance;

	final double alpha;
	final Collection< ? extends NonrigidIP > ips;

	final MovingLeastSquaresTransform2 transform;
	final AffineModel3D model;

	public VirtualGridRandomAccess(
			final long[] min,
			final long[] controlPointDistance,
			final double alpha,
			final Collection< ? extends NonrigidIP > ips,
			final int n )
	{
		super( n );

		this.min = min;
		this.controlPointDistance = controlPointDistance;
		this.alpha = alpha;
		this.ips = ips;
		this.transform = new MovingLeastSquaresTransform2();
		final ArrayList< PointMatch > matches = new ArrayList<>();

		for ( final NonrigidIP ip : ips )
			matches.add( new PointMatch( new Point( ip.getTargetW().clone() ), new Point( ip.getL().clone() ) ) );

		this.model = new AffineModel3D();

		transform.setAlpha( alpha );
		transform.setModel( model );
		try
		{
			transform.setMatches( matches );
		}
		catch ( NotEnoughDataPointsException | IllDefinedDataPointsException e )
		{
			e.printStackTrace();
			throw new RuntimeException( "VirtualGridRandomAccess: Unable to compute non-rigid grid: " + e );
		}
	}

	protected static final void getWorldCoordinates( final double[] pos, final int[] l, final long[] min, final long[] controlPointDistance, final int n )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = l[ d ] * controlPointDistance[ d ] + min[ d ];
	}

	@Override
	public NumericAffineModel3D get()
	{
		//System.out.print( Util.printCoordinates( position ) + " >>> " );

		getWorldCoordinates( pos, position, min, controlPointDistance, n );

		//System.out.print( Util.printCoordinates( pos ) );

		transform.applyInPlace( pos ); // also modifies the model

		//System.out.println( " >>> " + Util.printCoordinates( pos ) + ": " + model );

		return new NumericAffineModel3D( model.copy() );
	}

	@Override
	public Sampler< NumericAffineModel3D > copy()
	{
		return copyRandomAccess();
	}

	@Override
	public RandomAccess< NumericAffineModel3D > copyRandomAccess()
	{
		return new VirtualGridRandomAccess( min, controlPointDistance, alpha, ips, n );
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
