/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.legacy.segmentation;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mpicbg.imglib.algorithm.integral.IntegralImageLong;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussian.SpecialPoint;
import mpicbg.imglib.container.array.ArrayContainerFactory;
import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.cursor.array.ArrayCursor;
import mpicbg.imglib.cursor.special.LocalNeighborhoodCursor;
import mpicbg.imglib.cursor.special.LocalNeighborhoodCursorFactory;
import mpicbg.imglib.function.Converter;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.multithreading.Chunk;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.type.numeric.integer.LongType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.util.Util;
import net.preibisch.legacy.registration.detection.DetectionSegmentation;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.ImagePortion;

/**
 * An interactive tool for determining the required radius and peak threshold
 * 
 * @author Stephan Preibisch, Marwan Zouinkhi
 */
public class Integral {
	
	public Image<LongType> computeIntegralImage( final Image<FloatType> img )
	{
		IntegralImageLong< FloatType > intImg = new IntegralImageLong<FloatType>( img, new Converter< FloatType, LongType >()
		{
			@Override
			public void convert( final FloatType input, final LongType output ) { output.set( Util.round( input.get() ) ); } 
		} );
		
		intImg.process();
		
		final Image< LongType > integralImg = intImg.getResult();

		return integralImg;
	}
	
	final public static void computeDifferencOfMeanSlice( final Image< LongType> integralImg, final Image< FloatType > sliceImg, final int z, final int sx1, final int sy1, final int sz1, final int sx2, final int sy2, final int sz2, final float min, final float max  )
	{
		final float sumPixels1 = sx1 * sy1 * sz1;
		final float sumPixels2 = sx2 * sy2 * sz2;
		
		final int sx1Half = sx1 / 2;
		final int sy1Half = sy1 / 2;
		final int sz1Half = sz1 / 2;

		final int sx2Half = sx2 / 2;
		final int sy2Half = sy2 / 2;
		final int sz2Half = sz2 / 2;
		
		final int sxHalfMax = Math.max( sx1Half, sx2Half );
		final int syHalfMax = Math.max( sy1Half, sy2Half );
		final int szHalfMax = Math.max( sz1Half, sz2Half );

		final int w = sliceImg.getDimension( 0 ) - ( Math.max( sx1, sx2 ) / 2 ) * 2;
		final int h = sliceImg.getDimension( 1 ) - ( Math.max( sy1, sy2 ) / 2 ) * 2;
		final int d = (integralImg.getDimension( 2 ) - 1) - ( Math.max( sz1, sz2 ) / 2 ) * 2;
				
		final long imageSize = w * h;

		final AtomicInteger ai = new AtomicInteger(0);					
        final Thread[] threads = SimpleMultiThreading.newThreads();

        final Vector<Chunk> threadChunks = SimpleMultiThreading.divideIntoChunks( imageSize, threads.length );
		
        for (int ithread = 0; ithread < threads.length; ++ithread)
            threads[ithread] = new Thread(new Runnable()
            {
                public void run()
                {
                	// Thread ID
                	final int myNumber = ai.getAndIncrement();
        
                	// get chunk of pixels to process
                	final Chunk myChunk = threadChunks.get( myNumber );
                	final long loopSize = myChunk.getLoopSize();
                	
            		final LocalizableCursor< FloatType > cursor = sliceImg.createLocalizableCursor();
            		final LocalizableByDimCursor< LongType > randomAccess = integralImg.createLocalizableByDimCursor();

            		cursor.fwd( myChunk.getStartPosition() );
            		
            		// do as many pixels as wanted by this thread
                    for ( long j = 0; j < loopSize; ++j )
                    {                    	
            			final FloatType result = cursor.next();
            			
            			final int x = cursor.getPosition( 0 );
            			final int y = cursor.getPosition( 1 );
            			//final int z = cursor.getPosition( 2 );
            			
            			final int xt = x - sxHalfMax;
            			final int yt = y - syHalfMax;
            			final int zt = z - szHalfMax;
            			
            			if ( xt >= 0 && yt >= 0 && zt >= 0 && xt < w && yt < h && zt < d )
            			{
            				final float s1 = DOM.computeSum( x - sx1Half, y - sy1Half, z - sz1Half, sx1, sy1, sz1, randomAccess ) / sumPixels1;
            				final float s2 = DOM.computeSum( x - sx2Half, y - sy2Half, z - sz2Half, sx2, sy2, sz2, randomAccess ) / sumPixels2;
            			
            				result.set( ( s2 - s1 ) / ( max - min ) );
            			}
                    }
                }
            });
        
        SimpleMultiThreading.startAndJoin( threads );
 	}

	public static int computeRadius2( final int radius1 )
	{
		final float sensitivity = 1.25f;
		
        final float k = (float)DetectionSegmentation.computeK( sensitivity );
        final float[] radius = DetectionSegmentation.computeSigma( k, radius1 );
        
        int radius2 = Math.round( radius[ 1 ] );
        
        if ( radius2 <= radius1 + 1 )
        	radius2 = radius1 + 1;
        
        return radius2;
	}
	
	public static ArrayList<SimplePeak> findPeaks( final Image<FloatType> laPlace, final float minValue )
	{
		long numPixels = 1;

		for ( int d = 0; d < laPlace.getNumDimensions(); ++d )
			numPixels *= laPlace.getDimension( d );

		final int numDimensions = laPlace.getNumDimensions();
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( numPixels );
		final ArrayList< Callable< ArrayList< SimplePeak > > > tasks = new ArrayList<>();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< ArrayList< SimplePeak > >()
			{
				@Override
				public ArrayList< SimplePeak > call() throws Exception
				{
	            	final ArrayList<SimplePeak> myPeaks = new ArrayList<>();
	            	final LocalizableByDimCursor<FloatType> cursor = laPlace.createLocalizableByDimCursor();
	            	final LocalNeighborhoodCursor<FloatType> neighborhoodCursor = LocalNeighborhoodCursorFactory.createLocalNeighborhoodCursor( cursor );
	            	
	            	final int[] position = new int[ numDimensions ];
	            	final int[] dimensionsMinus2 = laPlace.getDimensions();

            		for ( int d = 0; d < numDimensions; ++d )
            			dimensionsMinus2[ d ] -= 2;

            		cursor.fwd( portion.getStartPosition() );

MainLoop:           for ( int l = 0; l < portion.getLoopSize(); ++l )
	                {
	                	cursor.fwd();
	                	cursor.getPosition( position );
	                	
                		for ( int d = 0; d < numDimensions; ++d )
                		{
                			final int pos = position[ d ];
                			
                			if ( pos < 1 || pos > dimensionsMinus2[ d ] )
                				continue MainLoop;
                		}

                		// if we do not clone it here, it might be moved along with the cursor
                		// depending on the container type used
                		final float currentValue = cursor.getType().get();
                		
                		// it can never be a desired peak as it is too low
                		if ( Math.abs( currentValue ) < minValue )
            				continue;

            			// update to the current position
            			neighborhoodCursor.update();

            			// we have to compare for example 26 neighbors in the 3d case (3^3 - 1) relative to the current position
            			final SpecialPoint specialPoint = isSpecialPoint( neighborhoodCursor, currentValue ); 
            			
            			if ( specialPoint == SpecialPoint.MIN )
            				myPeaks.add( new SimplePeak( position, Math.abs( currentValue ), true, false ) ); //( position, currentValue, specialPoint ) );
            			else if ( specialPoint == SpecialPoint.MAX )
            				myPeaks.add( new SimplePeak( position, Math.abs( currentValue ), false, true ) ); //( position, currentValue, specialPoint ) );
            			
            			// reset the position of the parent cursor
            			neighborhoodCursor.reset();
	                }

            		return myPeaks;
	    		}
			} );
		}

		// put together the list from the various threads	
		final ArrayList<SimplePeak> dogPeaks = new ArrayList<SimplePeak>();

		final ExecutorService taskExecutor = DeconViews.createExecutorService();

		try
		{
			for ( final Future< ArrayList< SimplePeak > > future : taskExecutor.invokeAll( tasks ) )
				dogPeaks.addAll( future.get() );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			e.printStackTrace();
		}

		taskExecutor.shutdown();

		return dogPeaks;
	}
	
	final protected static SpecialPoint isSpecialPoint( final LocalNeighborhoodCursor<FloatType> neighborhoodCursor, final float centerValue )
	{
		boolean isMin = true;
		boolean isMax = true;
				
		while ( (isMax || isMin) && neighborhoodCursor.hasNext() )
		{			
			neighborhoodCursor.fwd();
			
			final double value = neighborhoodCursor.getType().getRealDouble(); 
			
			// it can still be a minima if the current value is bigger/equal to the center value
			isMin &= (value >= centerValue);
			
			// it can still be a maxima if the current value is smaller/equal to the center value
			isMax &= (value <= centerValue);
		}		
		
		// this mixup is intended, a minimum in the 2nd derivation is a maxima in image space and vice versa
		if ( isMin )
			return SpecialPoint.MAX;
		else if ( isMax )
			return SpecialPoint.MIN;
		else
			return SpecialPoint.INVALID;
	}
	
	
	/**
	 * Normalize and make a copy of the {@link ImagePlus} into an {@link Image}&gt;FloatType&lt; for faster access when copying the slices
	 * 
	 * @param imp - the {@link ImagePlus} input image
	 * @param channel - channel
	 * @param timepoint - timepoint
	 * @return - the normalized copy [0...1]
	 */
	public static Image<FloatType> convertToFloat( final ImagePlus imp, int channel, int timepoint )
	{
		// stupid 1-offset of imagej
		channel++;
		timepoint++;
		
		final Image<FloatType> img;
		
		if ( imp.getNSlices() > 1 )
			img = new ImageFactory<FloatType>( new FloatType(), new ArrayContainerFactory() ).createImage( new int[]{ imp.getWidth(), imp.getHeight(), imp.getNSlices() } );
		else
			img = new ImageFactory<FloatType>( new FloatType(), new ArrayContainerFactory() ).createImage( new int[]{ imp.getWidth(), imp.getHeight() } );
		
		final int sliceSize = imp.getWidth() * imp.getHeight();
		
		int z = 0;
		ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) );
		
		if ( ip instanceof FloatProcessor )
		{
			final ArrayCursor<FloatType> cursor = (ArrayCursor<FloatType>)img.createCursor();
			
			float[] pixels = (float[])ip.getPixels();
			int i = 0;
			
			while ( cursor.hasNext() )
			{
				// only get new imageprocessor if necessary
				if ( i == sliceSize )
				{
					++z;
					
					pixels = (float[])imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) ).getPixels();
						 
					i = 0;
				}
				
				cursor.next().set( pixels[ i++ ] );
			}
		}
		else if ( ip instanceof ByteProcessor )
		{
			final ArrayCursor<FloatType> cursor = (ArrayCursor<FloatType>)img.createCursor();

			byte[] pixels = (byte[])ip.getPixels();
			int i = 0;
			
			while ( cursor.hasNext() )
			{
				// only get new imageprocessor if necessary
				if ( i == sliceSize )
				{
					++z;
					pixels = (byte[])imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) ).getPixels();
					
					i = 0;
				}
				
				cursor.next().set( pixels[ i++ ] & 0xff );
			}
		}
		else if ( ip instanceof ShortProcessor )
		{
			final ArrayCursor<FloatType> cursor = (ArrayCursor<FloatType>)img.createCursor();

			short[] pixels = (short[])ip.getPixels();
			int i = 0;
			
			while ( cursor.hasNext() )
			{
				// only get new imageprocessor if necessary
				if ( i == sliceSize )
				{
					++z;
					
					pixels = (short[])imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) ).getPixels();
					
					i = 0;
				}
				
				cursor.next().set( pixels[ i++ ] & 0xffff );
			}
		}
		else // some color stuff or so 
		{
			final LocalizableCursor<FloatType> cursor = img.createLocalizableCursor();
			final int[] location = new int[ img.getNumDimensions() ];

			while ( cursor.hasNext() )
			{
				cursor.fwd();
				cursor.getPosition( location );
				
				// only get new imageprocessor if necessary
				if ( location[ 2 ] != z )
				{
					z = location[ 2 ];
					
					ip = imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) );
				}
				
				cursor.getType().set( ip.getPixelValue( location[ 0 ], location[ 1 ] ) );
			}
		}
		
		// we do not want to normalize here ... otherwise the integral image will not work
		//ViewDataBeads.normalizeImage( img );
		
		return img;
	}
}
