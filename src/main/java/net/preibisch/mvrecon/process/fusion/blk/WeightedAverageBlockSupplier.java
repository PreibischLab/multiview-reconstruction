package net.preibisch.mvrecon.process.fusion.blk;

import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.util.Util.safeInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.algorithm.blocks.AbstractBlockSupplier;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.blocks.TempArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.Intervals;

class WeightedAverageBlockSupplier extends AbstractBlockSupplier< FloatType >
{
	private final int numDimensions;

	private final List< BlockSupplier< FloatType > > images;

	private final List< BlockSupplier< FloatType > > weights;

	private final TempArray< float[] >[] tempArrays;

	WeightedAverageBlockSupplier( final List< BlockSupplier< FloatType > > images, final List< BlockSupplier< FloatType > > weights )
	{
		this.numDimensions = images.get( 0 ).numDimensions();
		this.images = images;
		this.weights = weights;
		tempArrays = Cast.unchecked( new TempArray[ 4 ] );
		Arrays.setAll( tempArrays, i -> TempArray.forPrimitiveType( FLOAT ) );
	}

	private WeightedAverageBlockSupplier( final WeightedAverageBlockSupplier s )
	{
		numDimensions = s.numDimensions;
		images = new ArrayList<>( s.images.size() );
		weights = new ArrayList<>( s.weights.size() );
		s.images.forEach( i -> images.add( i.independentCopy() ) );
		s.weights.forEach( i -> weights.add( i.independentCopy() ) );
		tempArrays = Cast.unchecked( new TempArray[ 4 ] );
		Arrays.setAll( tempArrays, i -> TempArray.forPrimitiveType( FLOAT ) );
	}

	@Override
	public void copy( final long[] srcPos, final Object dest, final int[] size )
	{
		final int len = safeInt( Intervals.numElements( size ) );
		final float[] tmpI = tempArrays[ 0 ].get( len );
		final float[] tmpW = tempArrays[ 1 ].get( len );
		final float[] sumI = tempArrays[ 2 ].get( len );
		final float[] sumW = tempArrays[ 3 ].get( len );

		final int numImages = images.size();
		for ( int i = 0; i < numImages; i++ )
		{
			images.get( i ).copy( srcPos, tmpI, size );
			weights.get( i ).copy( srcPos, tmpW, size );
			if ( i == 0 )
			{
				for ( int x = 0; x < len; ++x )
				{
					sumI[ x ] = tmpW[ x ] * tmpI[ x ];
					sumW[ x ] = tmpW[ x ];
				}
			}
			else
			{
				for ( int x = 0; x < len; ++x )
				{
					sumI[ x ] += tmpW[ x ] * tmpI[ x ];
					sumW[ x ] += tmpW[ x ];
				}
			}
		}

		final float[] fdest = Cast.unchecked( dest );
		for ( int x = 0; x < len; ++x )
		{
			final float w = sumW[ x ];
			fdest[ x ] = ( w > 0 ) ? sumI[ x ] / w : 0;
		}
	}

	@Override
	public BlockSupplier< FloatType > independentCopy()
	{
		return new WeightedAverageBlockSupplier( this );
	}

	@Override
	public int numDimensions()
	{
		return numDimensions;
	}

	private static final FloatType type = new FloatType();

	@Override
	public FloatType getType()
	{
		return type;
	}
}
