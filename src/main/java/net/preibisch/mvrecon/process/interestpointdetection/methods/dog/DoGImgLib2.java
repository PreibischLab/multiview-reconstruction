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
package net.preibisch.mvrecon.process.interestpointdetection.methods.dog;


import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import bdv.util.ConstantRandomAccessible;
import ij.ImageJ;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussian.SpecialPoint;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.wrapper.ImgLib2;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.converter.BiConverter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.registration.bead.laplace.LaPlaceFunctions;
import net.preibisch.legacy.segmentation.SimplePeak;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.ImagePortion;
import net.preibisch.mvrecon.process.interestpointdetection.Localization;
import net.preibisch.mvrecon.process.interestpointdetection.methods.weightedgauss.Lazy;
import net.preibisch.mvrecon.process.interestpointdetection.methods.weightedgauss.WeightedGaussRA;
import util.ImgLib2Tools;

public class DoGImgLib2
{
	public static boolean silent = false;
	private static int[] blockSize = new int[] {96, 96, 64};

	public static void main ( String[] args )
	{
		new ImageJ();

		final RandomAccessibleInterval< FloatType > input = IOFunctions.openAs32BitArrayImg( new File( "/groups/scicompsoft/home/preibischs/Documents/SPIM/spim_TL18_Angle0.tif"))  ;
		final RandomAccessibleInterval< FloatType > mask = Views.interval(new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), input.numDimensions() ), input );

		//computeDoG(input, mask, 1.8015, 0.007973356, 1/*localization*/, false /*findMin*/, true /*findMax*/, Double.NaN, Double.NaN, DeconViews.createExecutorService(), Threads.numThreads() );

		//final RandomAccessibleInterval< FloatType > input2d = Views.hyperSlice(input, 2, 50 );

		long time = System.currentTimeMillis();

		// 1388x1040x81 = 116925120
		final ArrayList<InterestPoint> points = 
				computeDoG(input, mask, 2.000, 0.03, 1/*localization*/, false /*findMin*/, true /*findMax*/, 0, 255, Executors.newFixedThreadPool( 1 ), 1 );

		System.out.println( System.currentTimeMillis() - time );

		ImageJFunctions.show( input ).setRoi( mpicbg.ij.util.Util.pointsToPointRoi(points) );
	}

	public static < T extends RealType< T > > int radiusDoG( final double sigma )
	{
		final float k = LaPlaceFunctions.computeK( 4 );
		final int steps = 3;

		//
		// Compute the Sigmas for the gaussian convolution
		//
		final float imageSigma = 0.5f;

		final float[] sigmaStepsX = LaPlaceFunctions.computeSigma( steps, k, (float)sigma );
		final float[] sigmaStepsDiffX = LaPlaceFunctions.computeSigmaDiff( sigmaStepsX, imageSigma );

		final double sigma1 = sigmaStepsDiffX[0];
		final double sigma2 = sigmaStepsDiffX[1];

		final double[][] halfkernels = Gauss3.halfkernels( new double[] { Math.max( sigma1, sigma2 ) });

		return halfkernels[ 0 ].length - 1;
	}

	public static < T extends RealType< T > > ArrayList< InterestPoint > computeDoG(
			final RandomAccessibleInterval< T > input,
			final RandomAccessibleInterval< T > mask,
			final double sigma,
			final double threshold,
			final int localization,
			final boolean findMin,
			final boolean findMax,
			final double minIntensity,
			final double maxIntensity,
			final ExecutorService service,
			final int numThreads ) // for old imglib1-code
	{
		return computeDoG(input, mask, sigma, threshold, localization, findMin, findMax, minIntensity, maxIntensity, blockSize, service, numThreads);
	}

	public static < T extends RealType< T > > ArrayList< InterestPoint > computeDoG(
			final RandomAccessibleInterval< T > input,
			final RandomAccessibleInterval< T > mask,
			final double sigma,
			final double threshold,
			final int localization,
			final boolean findMin,
			final boolean findMax,
			final double minIntensity,
			final double maxIntensity,
			final int[] blockSize,
			final ExecutorService service,
			final int numThreads ) // for old imglib1-code
	{
		float initialSigma = (float)sigma;
		
		final float minPeakValue = (float)threshold;
		final float minInitialPeakValue;
		
		if ( localization == 0 )
			minInitialPeakValue = minPeakValue;
		else
			minInitialPeakValue = (float)threshold/10.0f;

		final float min, max;

		if ( Double.isNaN( minIntensity ) || Double.isNaN( maxIntensity ) || Double.isInfinite( minIntensity ) || Double.isInfinite( maxIntensity ) || minIntensity == maxIntensity )
		{
			final float[] minmax;
			
			if ( mask == null )
				minmax = FusionTools.minMax( input, service );
			else
				minmax = minMax( input, mask, service );

			min = minmax[ 0 ];
			max = minmax[ 1 ];
		}
		else
		{
			min = (float)minIntensity;
			max = (float)maxIntensity;
		}

		if ( !silent )
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): min intensity = " + min + ", max intensity = " + max );

		// normalize image
		final RandomAccessibleInterval< FloatType > inputFloat = ImgLib2Tools.normalizeVirtual( input, min, max );

		final float k = LaPlaceFunctions.computeK( 4 );
		final float K_MIN1_INV = LaPlaceFunctions.computeKWeight(k);
		final int steps = 3;
		
		//
		// Compute the Sigmas for the gaussian convolution
		//
		final double[] sigma1 = new double[ input.numDimensions() ];
		final double[] sigma2 = new double[ input.numDimensions() ];
		
		for ( int d = 0; d < input.numDimensions(); ++d )
		{
			final float[] sigmaStepsX = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
			final float[] sigmaStepsDiffX = LaPlaceFunctions.computeSigmaDiff( sigmaStepsX, 0.5f );
			
			sigma1[ d ] = sigmaStepsDiffX[0];
			sigma2[ d ] = sigmaStepsDiffX[1];
		}

		if ( !silent )
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): computing DoG with (sigma=" + initialSigma + ", " +
				"threshold=" + minPeakValue + ", sigma1=" + Util.printCoordinates( sigma1 ) + ", sigma2=" + Util.printCoordinates( sigma2 ) + ")" );

		final long[] minInterval = new long[ inputFloat.numDimensions() ];
		inputFloat.min( minInterval );

		final RandomAccessibleInterval< FloatType > gauss1, gauss2;
		final RandomAccessibleInterval< FloatType > maskFloat;

		if ( mask == null )
		{
			maskFloat = null;

			gauss1 = Views.translate( new ArrayImgFactory<>( new FloatType() ).create( inputFloat ), minInterval );
			gauss2 = Views.translate( new ArrayImgFactory<>( new FloatType() ).create( inputFloat ), minInterval );

			Gauss3.gauss(sigma1, Views.extendMirrorSingle( inputFloat ), gauss1, service);
			Gauss3.gauss(sigma2, Views.extendMirrorSingle( inputFloat ), gauss2, service);
		}
		else
		{
			maskFloat = ImgLib2Tools.convertVirtual( mask );

			gauss1 = computeGauss( inputFloat, maskFloat, new FloatType(), sigma1, blockSize );
			gauss2 = computeGauss( inputFloat, maskFloat, new FloatType(), sigma2, blockSize );
		}

		final RandomAccessibleInterval< FloatType > dog = Converters.convert(gauss2, gauss1, new BiConverter<FloatType, FloatType, FloatType>()
		{
			@Override
			public void convert( final FloatType inputA, final FloatType inputB, final FloatType output)
			{
				output.setReal( ( inputA.getRealDouble() - inputB.getRealDouble() ) * K_MIN1_INV );	
			}
		}, new FloatType() );

		//avoid double-caching for weighted gauss (i.e. mask != null)
		final RandomAccessibleInterval< FloatType > dogCached = (mask == null) ? FusionTools.cacheRandomAccessibleInterval( dog, new FloatType(), blockSize ) : dog;

		if ( !silent )
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Detecting peaks." );

		final ArrayList< SimplePeak > peaks = findPeaks( dogCached, maskFloat, minInitialPeakValue, service );

		final ArrayList< InterestPoint > finalPeaks;

		if ( localization == 0 )
		{
			finalPeaks = Localization.noLocalization( peaks, findMin, findMax, true );
		}
		else if ( localization == 1 )
		{
			// TODO: remove last Imglib1 crap
			final Img< FloatType > dogCopy = new ArrayImgFactory<>( new FloatType() ).create( dogCached );

			if ( !silent )
				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Mem-copying image." );

			FusionTools.copyImg( Views.zeroMin( dogCached ), dogCopy, service );
			final Image<mpicbg.imglib.type.numeric.real.FloatType> imglib1 = ImgLib2.wrapArrayFloatToImgLib1( dogCopy );

			for ( final SimplePeak peak : peaks )
				for ( int d = 0; d < peak.location.length; ++d )
					peak.location[ d ] -= minInterval[ d ];

			if ( !silent )
				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Quadratic localization." );

			finalPeaks = Localization.computeQuadraticLocalization( peaks, imglib1, findMin, findMax, minPeakValue, true, numThreads );

			// adjust detections for min coordinates of the RandomAccessibleInterval
			for ( final InterestPoint ip : finalPeaks )
			{
				for ( int d = 0; d < input.numDimensions(); ++d )
				{
					ip.getL()[ d ] += minInterval[ d ];
					ip.getW()[ d ] += minInterval[ d ];
				}
			}
		}
		else
		{
			finalPeaks = Localization.computeGaussLocalization( peaks, null, sigma, findMin, findMax, minPeakValue, true );
		}
		
		if ( !silent )
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );

		return finalPeaks;
	}

	public static < T extends RealType< T > & NativeType<T> > RandomAccessibleInterval< T > computeGauss(
			final RandomAccessibleInterval< T > input,
			final RandomAccessibleInterval< T > mask,
			final T type,
			final double[] sigma,
			final int[] blockSize )
	{
		final long[] min= new long[ input.numDimensions() ];
		input.min( min );

		final WeightedGaussRA< T > weightedgauss =
				new WeightedGaussRA<>(
						min,
						Views.extendMirrorSingle( input ),
						Views.extendZero( mask ),
						type.createVariable(),
						sigma );

		weightedgauss.total = new FinalInterval( input );

		final RandomAccessibleInterval<T> gauss = Views.translate( Lazy.process(new FinalInterval( input ), blockSize, type.createVariable(), AccessFlags.setOf(), weightedgauss ), min );
		//final Cache< ?, ? > gradientCache = ((CachedCellImg< ?, ? >)gradient).getCache();

		return gauss;

		//final RandomAccessibleInterval< T > output = Views.translate( new ArrayImgFactory<>(type).create( input ), min );
		//copy(gauss, output);
		//FusionTools.copyImg( (RandomAccessibleInterval)gauss, (RandomAccessibleInterval)output, DeconViews.createExecutorService() );
		//return output;
	}

	public static ArrayList<SimplePeak> findPeaks( final RandomAccessibleInterval< FloatType > laPlace, final RandomAccessibleInterval< FloatType > laPlaceMask, final float minValue, final ExecutorService service )
	{
		final Interval interval = Intervals.expand( laPlace, -1 );

		// create a view on the source with this interval
		final RandomAccessibleInterval< FloatType > source = Views.interval( laPlace, interval );
		final RandomAccessibleInterval< FloatType > mask;

		if ( laPlaceMask == null )
			mask = null;
		else
			mask = Views.interval( laPlaceMask, interval );

		long numPixels = 1;

		for ( int d = 0; d < source.numDimensions(); ++d )
			numPixels *= source.dimension( d );

		final int numDimensions = source.numDimensions();
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

					final Cursor< FloatType > center = Views.flatIterable( source ).localizingCursor();
					final Cursor< FloatType > centerMask;
					
					if ( mask == null )
						centerMask = null;
					else
						centerMask = Views.flatIterable( mask ).localizingCursor();

					 // instantiate a RectangleShape to access rectangular local neighborhoods
			        // of radius 1 (that is 3x3x...x3 neighborhoods), skipping the center pixel
			        // (this corresponds to an 8-neighborhood in 2d or 26-neighborhood in 3d, ...)
			        final RectangleShape shape = new RectangleShape( 1, true );

			        final Cursor< Neighborhood< FloatType >> neighborhoodCursor = shape.neighborhoods( source ).cursor();

			        final Cursor< Neighborhood< FloatType >> neighborhoodCursorMask;
			        if ( mask == null )
			        	neighborhoodCursorMask = null;
			        else
			        	neighborhoodCursorMask = shape.neighborhoods( mask ).cursor();

					center.jumpFwd( portion.getStartPosition() );
					neighborhoodCursor.jumpFwd( portion.getStartPosition() );
					if ( centerMask != null )
					{
						centerMask.jumpFwd( portion.getStartPosition() );
						neighborhoodCursorMask.jumpFwd( portion.getStartPosition() );
					}

	            	final int[] position = new int[ numDimensions ];

			        for ( int l = 0; l < portion.getLoopSize(); ++l )
	                {
			        	final FloatType centerValue = center.next();
			        	final Neighborhood< FloatType > neighborhood = neighborhoodCursor.next();
			        	
			        	// it can never be a desired peak if its outside the mask (or any pixel of the 3x3..3 neighborhood
                		if ( centerMask != null )
                		{
			        		centerMask.fwd();
			        		final Neighborhood< FloatType > neighborhoodMask = neighborhoodCursorMask.next();

			        		if ( centerMask.get().get() <= 0.0 )
                				continue;
                			
                			boolean outside = false;
                			for ( final FloatType value : neighborhoodMask )
                				if ( value.get() <= 0 )
                				{
                					outside = true;
                					break;
                				}

                			if ( outside )
                				continue;
                		}

			        	// it can never be a desired peak as it is too low
                		if ( Math.abs( centerValue.get() ) < minValue )
            				continue;

            			// we have to compare for example 26 neighbors in the 3d case (3^3 - 1) relative to the current position
            			final SpecialPoint specialPoint = isSpecialPoint( neighborhood.cursor(), centerValue.get() ); 

			        	center.localize( position );

            			if ( specialPoint == SpecialPoint.MIN )
            				myPeaks.add( new SimplePeak( position, Math.abs( centerValue.get() ), true, false ) ); //( position, currentValue, specialPoint ) );
            			else if ( specialPoint == SpecialPoint.MAX )
            				myPeaks.add( new SimplePeak( position, Math.abs( centerValue.get() ), false, true ) ); //( position, currentValue, specialPoint ) );
	                }

            		return myPeaks;
	    		}
			} );
		}

		// put together the list from the various threads	
		final ArrayList<SimplePeak> dogPeaks = new ArrayList<SimplePeak>();

		//final ExecutorService taskExecutor = DeconViews.createExecutorService();

		try
		{
			for ( final Future< ArrayList< SimplePeak > > future : service.invokeAll( tasks ) )
				dogPeaks.addAll( future.get() );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			e.printStackTrace();
		}

		//taskExecutor.shutdown();

		return dogPeaks;
	}

	final protected static SpecialPoint isSpecialPoint( final Cursor< FloatType > neighborhoodCursor, final float centerValue )
	{
		boolean isMin = true;
		boolean isMax = true;
				
		while ( (isMax || isMin) && neighborhoodCursor.hasNext() )
		{			
			neighborhoodCursor.fwd();
			
			final double value = neighborhoodCursor.get().getRealDouble(); 
			
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

	public static final <T extends Type<T>> void copy(
			final RandomAccessible<? extends T> source,
			final RandomAccessibleInterval<T> target) {

		Views.flatIterable(Views.interval(Views.pair(source, target), target)).forEach(
				pair -> pair.getB().set(pair.getA()));
	}

	public static < T extends RealType< T > > float[] minMax( final RandomAccessibleInterval< T > img, final RandomAccessibleInterval< T > mask, final ExecutorService service )
	{
		final IterableInterval< T > iterable = Views.iterable( img );

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( iterable.size() );

		// set up executor service
		//final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< float[] > > tasks = new ArrayList< Callable< float[] > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< float[] >() 
					{
						@Override
						public float[] call() throws Exception
						{
							float min = Float.MAX_VALUE;
							float max = -Float.MAX_VALUE;
							
							final Cursor< T > c = iterable.cursor();
							c.jumpFwd( portion.getStartPosition() );
							final RandomAccess< T > r = mask.randomAccess();

							for ( long j = 0; j < portion.getLoopSize(); ++j )
							{
								final float v = c.next().getRealFloat();
								r.setPosition( c );
								
								if ( r.get().getRealDouble() > 0 )
								{
									min = Math.min( min, v );
									max = Math.max( max, v );
								}
							}
							
							// min & max of this portion
							return new float[]{ min, max };
						}
					});
		}
		
		// run threads and combine results
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		
		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< float[] > > futures = service.invokeAll( tasks );
			
			for ( final Future< float[] > future : futures )
			{
				final float[] minmax = future.get();
				min = Math.min( min, minmax[ 0 ] );
				max = Math.max( max, minmax[ 1 ] );
			}
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute masked min/max: " + e );
			e.printStackTrace();
			return null;
		}

		//taskExecutor.shutdown();
		
		return new float[]{ min, max };
	}
}
