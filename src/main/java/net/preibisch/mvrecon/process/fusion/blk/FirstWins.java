/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2024 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.fusion.blk;

import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.FLOAT;
import static net.imglib2.util.Util.safeInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.algorithm.blocks.AbstractBlockSupplier;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.blocks.TempArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.Intervals;

class FirstWins
{
	public static BlockSupplier< FloatType > of(
			final List< BlockSupplier< FloatType > > images,
			final List< BlockSupplier< UnsignedByteType > > masks,
			final Overlap overlap )
	{
		return new FirstWinsBlockSupplier( images, masks, overlap );
	}

	private static class FirstWinsBlockSupplier extends AbstractBlockSupplier< FloatType >
	{
		private final int numDimensions;

		private final List< BlockSupplier< FloatType > > images;

		private final List< BlockSupplier< UnsignedByteType > > masks;

		private final Overlap overlap;

		private final TempArray< byte[] > tempArrayM;

		private final TempArray< byte[] > tempArrayAccM;

		private final TempArray< float[] > tempArrayI;

		FirstWinsBlockSupplier(
				final List< BlockSupplier< FloatType > > images,
				final List< BlockSupplier< UnsignedByteType > > masks,
				final Overlap overlap )
		{
			this.numDimensions = images.get( 0 ).numDimensions();
			this.images = images;
			this.masks = masks;
			this.overlap = overlap;
			tempArrayM = TempArray.forPrimitiveType( BYTE );
			tempArrayAccM = TempArray.forPrimitiveType( BYTE );
			tempArrayI = TempArray.forPrimitiveType( FLOAT );
		}

		private FirstWinsBlockSupplier( final FirstWinsBlockSupplier s )
		{
			numDimensions = s.numDimensions;
			images = new ArrayList<>( s.images.size() );
			masks = new ArrayList<>( s.masks.size() );
			s.images.forEach( i -> images.add( i.independentCopy() ) );
			s.masks.forEach( i -> masks.add( i.independentCopy() ) );
			overlap = s.overlap;
			tempArrayM = TempArray.forPrimitiveType( BYTE );
			tempArrayAccM = TempArray.forPrimitiveType( BYTE );
			tempArrayI = TempArray.forPrimitiveType( FLOAT );
		}

		@Override
		public void copy( final long[] srcPos, final Object dest, final int[] size )
		{
			final int len = safeInt( Intervals.numElements( size ) );

			final byte[] tmpM = tempArrayM.get( len );
			final byte[] accM = tempArrayAccM.get( len );
			final float[] tmpI = tempArrayI.get( len );
			final float[] fdest = Cast.unchecked( dest );

			// algorithm:
			//   create byte[] accM and fill with 0
			//   for ( i : overlapping ) {
			//       get masks(i) into tmpM byte[]
			//       iterate tmpM[x] and accM[x]
			//          if tmpM[x] == 1 and accM[x] == 0:
			//              set accM[x] = i+1
			//       if any accM was set:
			//           get images(i) into tmpI float[]
			//       iterate tmpM[x], tmpI[x] and fdest[x]
			//          if accM[x] == i+1:
			//              set fdest[i] = tmpI[x]
			//   }
			//   finally, where accM[x] == 0, set fdest[i] = 0

			Arrays.fill( accM, ( byte ) 0 );

	//		final long[] srcMax = new long[ srcPos.length ];
	//		Arrays.setAll( srcMax, d -> srcPos[ d ] + size[ d ] - 1 );
	//		final int[] overlapping = overlap.getOverlappingViewIndices( srcPos, srcMax );
	//		for ( int i : overlapping )
	//		{
	//			masks.get( i ).copy( srcPos, tmpM, size );
	//			final byte flag = ( byte ) ( i + 1 );
	//			boolean anySet = false;
	//			for ( int x = 0; x < len; ++x )
	//			{
	//				if ( tmpM[ x ] == 1 && accM[ x ] == 0 )
	//				{
	//					accM[ x ] = flag;
	//					anySet = true;
	//				}
	//			}
	//			if ( anySet )
	//			{
	//				images.get( i ).copy( srcPos, tmpI, size );
	//				for ( int x = 0; x < len; ++x )
	//				{
	//					if ( accM[ x ] == flag )
	//						fdest[ x ] = tmpI[ x ];
	//				}
	//			}
	//		}
	//		for ( int x = 0; x < len; ++x )
	//		{
	//			if ( accM[ x ] == 0 )
	//				fdest[ x ] = 0;
	//		}

			final long[] srcMax = new long[ srcPos.length ];
			Arrays.setAll( srcMax, d -> srcPos[ d ] + size[ d ] - 1 );
			final int[] overlapping = overlap.getOverlappingViewIndices( srcPos, srcMax );
			int remaining = len;
			for ( int i : overlapping )
			{
				masks.get( i ).copy( srcPos, tmpM, size );
				final byte flag = ( byte ) ( i + 1 );
				final int remainingBefore = remaining;
				for ( int x = 0; x < len; ++x )
				{
					if ( tmpM[ x ] == 1 && accM[ x ] == 0 )
					{
						accM[ x ] = flag;
						--remaining;
					}
				}
				if ( remaining != remainingBefore )
				{
					images.get( i ).copy( srcPos, tmpI, size );
					for ( int x = 0; x < len; ++x )
					{
						if ( accM[ x ] == flag )
							fdest[ x ] = tmpI[ x ];
					}
				}
				if ( remaining == 0 )
					return;
			}
			for ( int x = 0; x < len; ++x )
			{
				if ( accM[ x ] == 0 )
					fdest[ x ] = 0;
			}

			// TODO
			//   alternative to try:
			// 	   count down remaining accM[x]==0 from len
			//     when remaining==0 stop
			//     if after all masks remaining>0, then where accM[x] == 0, set fdest[i] = 0
		}

		@Override
		public BlockSupplier< FloatType > independentCopy()
		{
			return new FirstWinsBlockSupplier( this );
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
}
