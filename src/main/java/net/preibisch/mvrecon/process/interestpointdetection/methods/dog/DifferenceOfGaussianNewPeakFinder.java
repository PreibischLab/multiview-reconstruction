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

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.imglib.algorithm.OutputAlgorithm;
import mpicbg.imglib.algorithm.math.ImageCalculatorInPlace;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianReal1;
import mpicbg.imglib.function.Function;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.segmentation.InteractiveIntegral;
import net.preibisch.legacy.segmentation.SimplePeak;

public class DifferenceOfGaussianNewPeakFinder extends DifferenceOfGaussianReal1< FloatType >
{
	ArrayList< SimplePeak > simplePeaks;
	final double min;
	// TODO: Remove this once the bug fix is uploaded
	final double[] s1, s2;

	public DifferenceOfGaussianNewPeakFinder(
			final Image< FloatType> img, OutOfBoundsStrategyFactory< FloatType> outOfBoundsFactory,
			final double[] sigma1, final double[] sigma2, double minPeakValue, double normalizationFactor)
	{
		super( img, outOfBoundsFactory, sigma1, sigma2, minPeakValue, normalizationFactor );

		this.s1 = sigma1;
		this.s2 = sigma2;
		this.min = minPeakValue;
	}

	public ArrayList< SimplePeak > getSimplePeaks() { return simplePeaks; }

	@Override
	public ArrayList<DifferenceOfGaussianPeak< FloatType>> findPeaks( final Image< FloatType > laPlace )
	{
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Detecting peaks." );
		simplePeaks = InteractiveIntegral.findPeaks( laPlace, (float)min );

		return new ArrayList<DifferenceOfGaussianPeak< FloatType>>();
	}

	// TODO: This is fixed in Imglib1 itself, remove once this is uploaded!
	/**
	 * Has to be overwritten as the ImageCalculator does not do the right amount of threads
	 */
	@Override
	public boolean process()
	{
		//
		// perform the gaussian convolutions transferring it to the new (potentially higher precision) type T
		//
		final int divisor = getComputeConvolutionsParalell() ? 2 : 1;
		final OutputAlgorithm<FloatType> conv1 = getGaussianConvolution( s1, Math.max( 1, getNumThreads() / divisor ) );
		final OutputAlgorithm<FloatType> conv2 = getGaussianConvolution( s2, Math.max( 1, getNumThreads() / divisor ) );

		final Image<FloatType> gauss1, gauss2;
		
		if ( conv1.checkInput() && conv2.checkInput() )
		{
			final AtomicInteger ai = new AtomicInteger(0);
			Thread[] threads = SimpleMultiThreading.newThreads( divisor );

			for (int ithread = 0; ithread < threads.length; ++ithread)
				threads[ithread] = new Thread(new Runnable()
				{
					public void run()
					{
						final int myNumber = ai.getAndIncrement();
						if ( myNumber == 0 || !getComputeConvolutionsParalell() )
						{
							if ( !conv1.process() )
								System.out.println( "Cannot compute gaussian convolution 1: " + conv1.getErrorMessage() );
						}
						
						if ( myNumber == 1 || !getComputeConvolutionsParalell() )
						{
							if ( !conv2.process() )
								System.out.println( "Cannot compute gaussian convolution 2: " + conv2.getErrorMessage() );
						}
					}
				});
			
			SimpleMultiThreading.startAndJoin( threads );
		}
		else
		{
			gauss1 = gauss2 = null;
			return false;
		}

		if ( conv1.getErrorMessage().length() == 0 && conv2.getErrorMessage().length() == 0 )
		{
			gauss1 = conv1.getResult();
			gauss2 = conv2.getResult();
		}
		else
		{
			gauss1 = gauss2 = null;
			return false;
		}
		
		//
		// subtract the images to get the LaPlace image
		//
		final Function<FloatType, FloatType, FloatType> function = getNormalizedSubtraction();
		final ImageCalculatorInPlace<FloatType, FloatType> imageCalc = new ImageCalculatorInPlace<FloatType, FloatType>( gauss2, gauss1, function );

		imageCalc.setNumThreads( getNumThreads() );

		if ( !imageCalc.checkInput() || !imageCalc.process() )
		{
			gauss1.close();
			gauss2.close();
			
			return false;
		}
		
		gauss1.close();

		peaks.clear();
		peaks.addAll( findPeaks( gauss2 ) );

		if ( getKeepDoGImage() )
			dogImage = gauss2;
		else
			gauss2.close(); 

		return true;
	}
}
