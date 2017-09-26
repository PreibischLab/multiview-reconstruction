package net.preibisch.mvrecon.process.fusion.transformed;

import java.util.List;

import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class FusedRandomAccessNoWeights extends AbstractLocalizableInt implements RandomAccess< FloatType >
{
	final List< ? extends RandomAccessible< FloatType > > images;

	final int numImages;
	final RandomAccess< ? extends RealType< ? > >[] i;

	final FloatType value = new FloatType();

	public FusedRandomAccessNoWeights(
			final int n,
			final List< ? extends RandomAccessible< FloatType > > images )
	{
		super( n );

		this.images = images;

		this.numImages = images.size();

		this.i = new RandomAccess[ numImages ];

		for ( int j = 0; j < numImages; ++j )
			this.i[ j ] = images.get( j ).randomAccess();
	}

	@Override
	public FloatType get()
	{
		double sumI = 0;

		for ( int j = 0; j < numImages; ++j )
			sumI += i[ j ].get().getRealDouble();

		value.set( (float)( sumI ) );

		return value;
	}

	@Override
	public FusedRandomAccessNoWeights copy()
	{
		return copyRandomAccess();
	}

	@Override
	public FusedRandomAccessNoWeights copyRandomAccess()
	{
		final FusedRandomAccessNoWeights r = new FusedRandomAccessNoWeights( n, images );
		r.setPosition( this );
		return r;
	}

	@Override
	public void fwd( final int d )
	{
		++position[ d ];

		for ( int j = 0; j < numImages; ++j )
			i[ j ].fwd( d );
	}

	@Override
	public void bck( final int d )
	{
		--position[ d ];

		for ( int j = 0; j < numImages; ++j )
			i[ j ].bck( d );
	}

	@Override
	public void move( final int distance, final int d )
	{
		position[ d ] += distance;

		for ( int j = 0; j < numImages; ++j )
			i[ j ].move( distance, d );
	}

	@Override
	public void move( final long distance, final int d )
	{
		position[ d ] += distance;

		for ( int j = 0; j < numImages; ++j )
			i[ j ].move( distance, d );
	}

	@Override
	public void move( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += localizable.getIntPosition( d );

		for ( int j = 0; j < numImages; ++j )
			i[ j ].move( localizable );
	}

	@Override
	public void move( final int[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];

		for ( int j = 0; j < numImages; ++j )
			i[ j ].move( distance );
	}

	@Override
	public void move( final long[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];

		for ( int j = 0; j < numImages; ++j )
			i[ j ].move( distance );
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		localizable.localize( position );

		for ( int j = 0; j < numImages; ++j )
			i[ j ].setPosition( localizable );
	}

	@Override
	public void setPosition( final int[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = pos[ d ];

		for ( int j = 0; j < numImages; ++j )
			i[ j ].setPosition( pos );
	}

	@Override
	public void setPosition( final long[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = ( int ) pos[ d ];

		for ( int j = 0; j < numImages; ++j )
			i[ j ].setPosition( pos );
	}

	@Override
	public void setPosition( final int pos, final int d )
	{
		position[ d ] = pos;

		for ( int j = 0; j < numImages; ++j )
			i[ j ].setPosition( pos, d );
	}

	@Override
	public void setPosition( final long pos, final int d )
	{
		position[ d ] = ( int ) pos;

		for ( int j = 0; j < numImages; ++j )
			i[ j ].setPosition( pos, d );
	}
}
