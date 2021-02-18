/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bdv.ViewerSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import util.ImgLib2Tools;

public class SplitViewerSetupImgLoader implements ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType >, MultiResolutionSetupImgLoader< UnsignedShortType >
{
	final ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > underlyingSetupImgLoader;
	final Interval interval;
	final Dimensions size;
	final int n;

	final double[][] mipmapResolutions;
	final AffineTransform3D[] mipmapTransforms;
	final Dimensions[] sizes;
	final Interval[] scaledIntervals;

	private boolean[] isUpdated;

	public SplitViewerSetupImgLoader( final ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > underlyingSetupImgLoader, final Interval interval )
	{
		this.underlyingSetupImgLoader = underlyingSetupImgLoader;
		this.interval = interval;
		this.n = interval.numDimensions();

		final long[] dim = new long[ interval.numDimensions() ];
		interval.dimensions( dim );

		this.size = new FinalDimensions( dim );

		final int levels = underlyingSetupImgLoader.numMipmapLevels();
		this.sizes = new Dimensions[ levels ];
		this.scaledIntervals = new Interval[ levels ];
		this.mipmapResolutions = underlyingSetupImgLoader.getMipmapResolutions();
		this.mipmapTransforms = new AffineTransform3D[ levels ];

		this.isUpdated = new boolean[ levels ];
		for ( int l = 0; l < levels; ++l )
			this.isUpdated[ l ] = false;

		setUpMultiRes( levels, n, interval, mipmapResolutions, mipmapTransforms, sizes, scaledIntervals, underlyingSetupImgLoader.getMipmapTransforms() );
	}

	protected static final void setUpMultiRes(
			final int levels,
			final int n,
			final Interval interval,
			final double[][] mipmapResolutions,
			final AffineTransform3D[] mipmapTransforms,
			final Dimensions[] sizes,
			final Interval[] scaledIntervals,
			final AffineTransform3D[] oldmipmapTransforms )
	{
		// precompute intervals and new mipmaptransforms (because of rounding of interval borders)
		for ( int level = 0; level < levels; ++level )
		{
			final double[] min = new double[ n ];
			final double[] max = new double[ n ];
	
			final long[] minL = new long[ n ];
			final long[] maxL = new long[ n ];
			final long[] size = new long[ n ];

			for ( int d = 0; d < n; ++d )
			{
				min[ d ] = interval.realMin( d ) / mipmapResolutions[ level ][ d ];
				max[ d ] = (interval.realMax( d ) - 0.01) / mipmapResolutions[ level ][ d ];

				minL[ d ] = Math.round( Math.floor( min[ d ] ) );
				maxL[ d ] = Math.round( Math.floor( max[ d ] ) );

				size[ d ] = maxL[ d ] - minL[ d ] + 1;
			}

			sizes[ level ] = new FinalDimensions( size );
			scaledIntervals[ level ] = new FinalInterval( minL, maxL );

			final AffineTransform3D mipMapTransform = oldmipmapTransforms[ level ].copy();

			// the additional downsampling (performed below)
			final AffineTransform3D additonalTranslation = new AffineTransform3D();
			additonalTranslation.set(
					1.0, 0.0, 0.0, (minL[ 0 ] - min[ 0 ]),
					0.0, 1.0, 0.0, (minL[ 1 ] - min[ 1 ]),
					0.0, 0.0, 1.0, (minL[ 2 ] - min[ 2 ]) );
	
			mipMapTransform.concatenate( additonalTranslation );
			mipmapTransforms[ level ] = mipMapTransform;
		}
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final int timepointId, final ImgLoaderHint... hints )
	{
		IOFunctions.println( "requesting full size: " );

		return Views.zeroMin( Views.interval( underlyingSetupImgLoader.getImage( timepointId, hints ), interval ) );
	}

	@Override
	public UnsignedShortType getImageType()
	{
		return underlyingSetupImgLoader.getImageType();
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, final boolean normalize, final ImgLoaderHint... hints )
	{
		if ( normalize )
			return ImgLib2Tools.normalizeVirtual( getImage( timepointId, hints ) );
		else
			return ImgLib2Tools.convertVirtual( getImage( timepointId, hints ) );
	}

	@Override
	public Dimensions getImageSize( final int timepointId )
	{
		return size;
	}

	@Override
	public VoxelDimensions getVoxelSize( final int timepointId )
	{
		throw new RuntimeException( "not supported." );
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		/*
		System.out.println( "requesting: " + level );

		for ( int l = 0; l < mipmapResolutions.length; ++l )
		{
			System.out.println( "level " + l + ": " + mipmapTransforms[ l ] );
			System.out.println( "level " + l + ": " + Util.printInterval( scaledIntervals[ l ] ) );
			System.out.print( "level " + l + ": " );
			for ( int d = 0; d < mipmapResolutions[ l ].length; ++d )
				System.out.print( mipmapResolutions[ l ][ d ] + "x" );
			System.out.println();
		}

		// 164 is a problem
		final RandomAccessibleInterval< UnsignedShortType > full = underlyingSetupImgLoader.getImage( timepointId, level, hints );

		updateScaledIntervals( this.scaledIntervals, level, n, full );

		final RandomAccessibleInterval img = Views.zeroMin( Views.interval( full, scaledIntervals[ level ] ) );

		if ( level == 3 && img.dimension( 0  ) == 33 )
		{
			DisplayImage.getImagePlusInstance( full, false, "levefull=" + level, 0.0, 255.0 ).show();;
			DisplayImage.getImagePlusInstance( img, false, "level=" + level, 0.0, 255.0 ).show();;
		}

		System.out.println( "size: " + Util.printInterval( img ) );
		System.out.println( "interval: " + Util.printInterval( scaledIntervals[ level ] ) ); */

		final RandomAccessibleInterval< UnsignedShortType > full = underlyingSetupImgLoader.getImage( timepointId, level, hints );

		updateScaledIntervals( this.scaledIntervals, level, n, full );

		return Views.zeroMin( Views.interval( full, scaledIntervals[ level ] ) );
	}

	/**
	 * Sometimes because of scaling the max is too high exceeding the actual downsampled image as provided
	 *
	 * @param scaledIntervals - the current scaled intervals (will be updated)
	 * @param level - which level
	 * @param n - num dimensions
	 * @param fullImg - the full interval as currently loaded
	 */
	protected final void updateScaledIntervals( final Interval[] scaledIntervals, final int level, final int n, final Interval fullImg )
	{
		if ( !isUpdated[ level ] )
		{
			synchronized ( this )
			{
				if ( isUpdated[ level ] )
					return;

				isUpdated[ level ] = true;

				boolean updateScaledInterval = false;
		
				for ( int d = 0; d < n; ++d )
					if ( scaledIntervals[ level ].max( d ) >= fullImg.max( d ) )
						updateScaledInterval = true;
		
				if ( updateScaledInterval )
				{
					final long[] min = new long[ n ];
					final long[] max = new long[ n ];
		
					for ( int d = 0; d < n; ++d )
					{
						min[ d ] = scaledIntervals[ level ].min( d );
						max[ d ] = Math.min( scaledIntervals[ level ].max( d ), fullImg.max( d ) );
					}
		
					scaledIntervals[ level ] = new FinalInterval( min, max );
				}
			}
		}
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedShortType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		final RandomAccessibleInterval< VolatileUnsignedShortType > full = underlyingSetupImgLoader.getVolatileImage( timepointId, level, hints );

		updateScaledIntervals( this.scaledIntervals, level, n, full );

		return Views.zeroMin( Views.interval( underlyingSetupImgLoader.getVolatileImage( timepointId, level, hints ), scaledIntervals[ level ] ) );
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return mipmapResolutions;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}

	@Override
	public int numMipmapLevels()
	{
		return underlyingSetupImgLoader.numMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, final int level, final boolean normalize, final ImgLoaderHint... hints )
	{
		final RandomAccessibleInterval< UnsignedShortType > ushortImg = getImage( timepointId, level, hints );

		// copy unsigned short img to float img

		// create float img
		final FloatType f = new FloatType();
		final ImgFactory< FloatType > imgFactory;
		if ( Intervals.numElements( ushortImg ) <= Integer.MAX_VALUE )
		{
			imgFactory = new ArrayImgFactory<>( f );
		}
		else
		{
			final long[] dimsLong = new long[ ushortImg.numDimensions() ];
			ushortImg.dimensions( dimsLong );
			final int[] cellDimensions = new int[ dimsLong.length ];
			for ( int i = 0; i < cellDimensions.length; ++i )
				cellDimensions[ i ] = 64;

			imgFactory = new CellImgFactory<>( f, cellDimensions );
		}
		final Img< FloatType > floatImg = imgFactory.create( ushortImg );

		// set up executor service
		final int numProcessors = Runtime.getRuntime().availableProcessors();
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( numProcessors );
		final ArrayList< Callable< Void > > tasks = new ArrayList<>();

		// set up all tasks
		final int numPortions = numProcessors * 2;
		final long threadChunkSize = floatImg.size() / numPortions;
		final long threadChunkMod = floatImg.size() % numPortions;

		for ( int portionID = 0; portionID < numPortions; ++portionID )
		{
			// move to the starting position of the current thread
			final long startPosition = portionID * threadChunkSize;

			// the last thread may has to run longer if the number of pixels cannot be divided by the number of threads
			final long loopSize = ( portionID == numPortions - 1 ) ? threadChunkSize + threadChunkMod : threadChunkSize;

			if ( Views.iterable( ushortImg ).iterationOrder().equals( floatImg.iterationOrder() ) )
			{
				tasks.add( new Callable< Void >()
				{
					@Override
					public Void call() throws Exception
					{
						final Cursor< UnsignedShortType > in = Views.iterable( ushortImg ).cursor();
						final Cursor< FloatType > out = floatImg.cursor();

						in.jumpFwd( startPosition );
						out.jumpFwd( startPosition );

						for ( long j = 0; j < loopSize; ++j )
							out.next().set( in.next().getRealFloat() );

						return null;
					}
				} );
			}
			else
			{
				tasks.add( new Callable< Void >()
				{
					@Override
					public Void call() throws Exception
					{
						final Cursor< UnsignedShortType > in = Views.iterable( ushortImg ).localizingCursor();
						final RandomAccess< FloatType > out = floatImg.randomAccess();

						in.jumpFwd( startPosition );

						for ( long j = 0; j < loopSize; ++j )
						{
							final UnsignedShortType vin = in.next();
							out.setPosition( in );
							out.get().set( vin.getRealFloat() );
						}

						return null;
					}
				} );
			}
		}

		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
			taskExecutor.shutdown();
		}
		catch ( final InterruptedException e )
		{
			return null;
		}

		if ( normalize )
			// normalize the image to 0...1
			normalize( floatImg );

		return floatImg;
	}

	/**
	 * normalize img to 0...1
	 */
	protected static void normalize( final IterableInterval< FloatType > img )
	{
		float currentMax = img.firstElement().get();
		float currentMin = currentMax;
		for ( final FloatType t : img )
		{
			final float f = t.get();
			if ( f > currentMax )
				currentMax = f;
			else if ( f < currentMin )
				currentMin = f;
		}

		final float scale = ( float ) ( 1.0 / ( currentMax - currentMin ) );
		for ( final FloatType t : img )
			t.set( ( t.get() - currentMin ) * scale );

		/*
		 * Once we do not need Img's for DoG anymore ....

		final RandomAccessibleInterval< UnsignedShortType > image = getImage( timepointId, level, hints );
		final RandomAccessibleInterval< FloatType > floatImg = Converters.convert( image, new RealFloatConverter< UnsignedShortType >(), new FloatType() );
		if (normalize)
			AbstractImgLoader.normalize( floatImg );
		return floatImg;
		*/
	}

	@Override
	public Dimensions getImageSize( final int timepointId, final int level )
	{
		return sizes[ level ];
	}

	@Override
	public VolatileUnsignedShortType getVolatileImageType()
	{
		return underlyingSetupImgLoader.getVolatileImageType();
	}
}
