/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2014 - 2017 Board of Regents of the University of
 * Wisconsin-Madison, University of Konstanz and Brian Northan.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
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
