/*
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2020 Multiview Reconstruction developers.
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

package net.preibisch.mvrecon.process.interestpointdetection.methods.weightedgauss;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.gauss3.SeparableSymmetricConvolution;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * Simple Gaussian filter Op
 *
 * @author Stephan Saalfeld
 * @author Stephan Preibisch
 * @param <T> type of input and output
 */
public class WeightedGaussRA<T extends RealType<T> & NativeType<T>> implements Consumer<RandomAccessibleInterval<T>>
{
	final T type;
	final private double[] sigmas;
	final long[] globalMin;
	final private RandomAccessible<T> source, weight;

	public WeightedGaussRA(
			final long[] min,
			final RandomAccessible<T> source,
			final RandomAccessible<T> weight,
			final T type,
			final double[] sigmas)
	{
		this.source = source;
		this.weight = weight;
		this.globalMin = min;
		this.type = type;
		this.sigmas = sigmas;
	}

	// Note: the output RAI typically sits at 0,0...0 because it usually is a CachedCellImage
	// (but the actual interval to process in many blocks sits somewhere else) 
	@Override
	public void accept( final RandomAccessibleInterval<T> output )
	{
		try
		{
			final WeightedRandomAccessible< T > weightedSource = new WeightedRandomAccessible< T >( source, weight, type );

			final long[] min= new long[ output.numDimensions() ];
			for ( int d = 0; d < min.length; ++d )
				min[ d ] = globalMin[ d ] + output.min( d );

			final RandomAccessibleInterval< T > sourceTmp = Views.translate( new ArrayImgFactory<>(type).create( output ), min );
			final RandomAccessibleInterval< T > weightTmp = Views.translate( new ArrayImgFactory<>(type).create( output ), min );

			final ExecutorService service = Executors.newSingleThreadExecutor();

			SeparableSymmetricConvolution.convolve(
					Gauss3.halfkernels(sigmas),
					weightedSource,
					sourceTmp,
					service );

			SeparableSymmetricConvolution.convolve(
					Gauss3.halfkernels(sigmas),
					weight,
					weightTmp,
					service );

			service.shutdown();

			final Cursor< T > i = Views.flatIterable( Views.interval( source, sourceTmp ) ).cursor();
			final Cursor< T > s = Views.flatIterable( sourceTmp ).cursor();
			final Cursor< T > w = Views.flatIterable( weightTmp ).cursor();
			final Cursor< T > o = Views.flatIterable( output ).cursor();

			while ( o.hasNext() )
			{
				final double weight = w.next().getRealDouble();
	
				if ( weight == 0 )
				{
					o.next().set( i.next() );
					s.fwd();
				}
				else
				{
					o.next().setReal( s.next().getRealDouble() / weight );
					i.fwd();
				}
			}

		}
		catch (final IncompatibleTypeException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static class WeightedRandomAccessible< T extends RealType< T > > implements RandomAccessible< T >
	{
		final RandomAccessible< T > source, weight;
		final T type;

		public WeightedRandomAccessible(
				final RandomAccessible< T > source,
				final RandomAccessible< T > weight,
				final T type )
		{
			this.source = source;
			this.weight = weight;
			this.type = type;
		}

		@Override
		public int numDimensions()
		{
			return source.numDimensions();
		}

		@Override
		public RandomAccess< T > randomAccess()
		{
			return new WeightedRandomAccess< T >( source.randomAccess(), weight.randomAccess(), type.createVariable() );
		}

		@Override
		public RandomAccess< T > randomAccess( Interval interval )
		{
			return randomAccess();
		}
	}

	public static class WeightedRandomAccess< T extends RealType< T > > implements RandomAccess< T >
	{
		final RandomAccess< T > source, weight;
		final T type;

		public WeightedRandomAccess(
				final RandomAccess< T > source,
				final RandomAccess< T > weight,
				final T type )
		{
			this.source = source;
			this.weight = weight;
			this.type = type;
		}

		@Override
		public T get()
		{
			type.setReal( source.get().getRealDouble() * weight.get().getRealDouble() );
			return type;
		}

		@Override
		public long getLongPosition( final int d )
		{
			return source.getLongPosition( d );
		}

		@Override
		public int numDimensions()
		{
			return source.numDimensions();
		}

		@Override
		public void fwd( final int d )
		{
			source.fwd( d );
			weight.fwd( d );
		}

		@Override
		public void bck( final int d )
		{
			source.bck( d );
			weight.bck( d );
		}

		@Override
		public void move( final int distance, final int d )
		{
			source.move( distance, d );
			weight.move( distance, d );
		}

		@Override
		public void move( final long distance, final int d )
		{
			source.move( distance, d );
			weight.move( distance, d );
		}

		@Override
		public void move( final Localizable distance )
		{
			source.move( distance );
			weight.move( distance );
		}

		@Override
		public void move( final int[] distance )
		{
			source.move( distance );
			weight.move( distance );
		}

		@Override
		public void move( final long[] distance )
		{
			source.move( distance );
			weight.move( distance );
		}

		@Override
		public void setPosition( final Localizable position )
		{
			source.setPosition( position );
			weight.setPosition( position );
		}

		@Override
		public void setPosition( final int[] position )
		{
			source.setPosition( position );
			weight.setPosition( position );
		}

		@Override
		public void setPosition( final long[] position )
		{
			source.setPosition( position );
			weight.setPosition( position );
		}

		@Override
		public void setPosition( final int position, final int d )
		{
			source.setPosition( position, d );
			weight.setPosition( position, d );
		}

		@Override
		public void setPosition( final long position, final int d )
		{
			source.setPosition( position, d );
			weight.setPosition( position, d );
		}

		@Override
		public Sampler< T > copy()
		{
			return copyRandomAccess();
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			return new WeightedRandomAccess<>( source.copyRandomAccess(), weight.copyRandomAccess(), type.createVariable() );
		}
	}
}
