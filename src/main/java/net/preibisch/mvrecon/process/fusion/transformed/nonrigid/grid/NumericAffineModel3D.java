package net.preibisch.mvrecon.process.fusion.transformed.nonrigid.grid;

import mpicbg.models.AffineModel3D;
import net.imglib2.type.numeric.NumericType;

public class NumericAffineModel3D implements NumericType< NumericAffineModel3D >
{
	final AffineModel3D model;
	final double[] data1 = new double[ 12 ];
	final double[] data2 = new double[ 12 ];

	public NumericAffineModel3D( final AffineModel3D model )
	{
		this.model = model;
	}

	public AffineModel3D getModel()
	{
		return model;
	}

	@Override
	public NumericAffineModel3D createVariable()
	{
		return new NumericAffineModel3D( new AffineModel3D() );
	}

	@Override
	public NumericAffineModel3D copy()
	{
		return new NumericAffineModel3D( model.copy() );
	}

	@Override
	public void set( final NumericAffineModel3D c )
	{
		this.model.set( c.model );
	}

	@Override
	public boolean valueEquals( final NumericAffineModel3D t )
	{
		model.toArray( data1 );
		t.model.toArray( data2 );

		for ( int i = 0; i < data1.length; ++i )
			if ( data1[ i ] != data2[ i ] )
				return false;

		return true;
	}

	@Override
	public void add( NumericAffineModel3D c )
	{
		model.toArray( data1 );
		c.model.toArray( data2 );

		for ( int i = 0; i < data1.length; ++i )
			data1[ i ] += data2[ i ];

		model.set(
				data1[ 0 ], data1[ 3 ], data1[ 6 ], data1[ 9 ],
				data1[ 1 ], data1[ 4 ], data1[ 7 ], data1[ 10 ],
				data1[ 2 ], data1[ 5 ], data1[ 8 ], data1[ 11 ] );
	}

	@Override
	public void mul( NumericAffineModel3D c )
	{
		model.toArray( data1 );
		c.model.toArray( data2 );

		for ( int i = 0; i < data1.length; ++i )
			data1[ i ] *= data2[ i ];

		model.set(
				data1[ 0 ], data1[ 3 ], data1[ 6 ], data1[ 9 ],
				data1[ 1 ], data1[ 4 ], data1[ 7 ], data1[ 10 ],
				data1[ 2 ], data1[ 5 ], data1[ 8 ], data1[ 11 ] );
	}

	@Override
	public void sub( NumericAffineModel3D c )
	{
		model.toArray( data1 );
		c.model.toArray( data2 );

		for ( int i = 0; i < data1.length; ++i )
			data1[ i ] -= data2[ i ];

		model.set(
				data1[ 0 ], data1[ 3 ], data1[ 6 ], data1[ 9 ],
				data1[ 1 ], data1[ 4 ], data1[ 7 ], data1[ 10 ],
				data1[ 2 ], data1[ 5 ], data1[ 8 ], data1[ 11 ] );
	}

	@Override
	public void div( NumericAffineModel3D c )
	{
		model.toArray( data1 );
		c.model.toArray( data2 );

		for ( int i = 0; i < data1.length; ++i )
			data1[ i ] /= data2[ i ];

		model.set(
				data1[ 0 ], data1[ 3 ], data1[ 6 ], data1[ 9 ],
				data1[ 1 ], data1[ 4 ], data1[ 7 ], data1[ 10 ],
				data1[ 2 ], data1[ 5 ], data1[ 8 ], data1[ 11 ] );
	}

	@Override
	public void setOne()
	{
		model.set( new AffineModel3D() );
	}

	@Override
	public void setZero()
	{
		model.set(
				0, 0, 0, 0,
				0, 0, 0, 0,
				0, 0, 0, 0 );
	}

	@Override
	public void mul( final float c )
	{
		model.toArray( data1 );

		for ( int i = 0; i < data1.length; ++i )
			data1[ i ] *= c;

		model.set(
				data1[ 0 ], data1[ 3 ], data1[ 6 ], data1[ 9 ],
				data1[ 1 ], data1[ 4 ], data1[ 7 ], data1[ 10 ],
				data1[ 2 ], data1[ 5 ], data1[ 8 ], data1[ 11 ] );
	}

	@Override
	public void mul( final double c )
	{
		model.toArray( data1 );

		for ( int i = 0; i < data1.length; ++i )
			data1[ i ] *= c;

		model.set(
				data1[ 0 ], data1[ 3 ], data1[ 6 ], data1[ 9 ],
				data1[ 1 ], data1[ 4 ], data1[ 7 ], data1[ 10 ],
				data1[ 2 ], data1[ 5 ], data1[ 8 ], data1[ 11 ] );
	}
}
