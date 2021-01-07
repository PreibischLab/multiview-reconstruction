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
import java.util.List;
import java.util.concurrent.ExecutorService;

import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.registration.bead.laplace.LaPlaceFunctions;
import net.preibisch.legacy.segmentation.SimplePeak;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.cuda.CUDADevice;
import net.preibisch.mvrecon.process.cuda.CUDASeparableConvolution;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointdetection.Localization;
import util.ImgLib1Convert;

public class ProcessDOG
{
	/*
	 * @param deviceList - a list of CUDA capable devices (or null if classic CPU computation in Java)
	 * @param accurateCUDA - use accurate CUDA implementation (including out of bounds or not)
	 * @param img - ImgLib1 and image (based on same image data, must be a wrap)
	 * @param sigma
	 * @param threshold
	 * @param localization
	 * @param imageSigmaX
	 * @param imageSigmaY
	 * @param imageSigmaZ
	 * @param findMin
	 * @param findMax
	 * @param minIntensity
	 * @param maxIntensity
	 * @return
	 */
	public static ArrayList< InterestPoint > compute(
			final CUDASeparableConvolution cuda,
			final List< CUDADevice > deviceList,
			final boolean accurateCUDA,
			final double percentGPUMem,
			final ExecutorService service,
			final int numThreads,
			final ImgLib1Convert img,
			final float sigma, 
			final float threshold, 
			final int localization,
			final double imageSigmaX,
			final double imageSigmaY,
			final double imageSigmaZ,
			final boolean findMin, 
			final boolean findMax,
			final double minIntensity,
			final double maxIntensity,
			final boolean keepIntensity )
	{
		float initialSigma = sigma;
		
		final float minPeakValue = threshold;
		final float minInitialPeakValue;
		
		if ( localization == 0 )
			minInitialPeakValue = minPeakValue;
		else
			minInitialPeakValue = threshold/10.0f;

		final float min, max;

		if ( Double.isNaN( minIntensity ) || Double.isNaN( maxIntensity ) || Double.isInfinite( minIntensity ) || Double.isInfinite( maxIntensity ) || minIntensity == maxIntensity )
		{
			final float[] minmax = FusionTools.minMax( img.imglib2Img() );
			min = minmax[ 0 ];
			max = minmax[ 1 ];
		}
		else
		{
			min = (float)minIntensity;
			max = (float)maxIntensity;
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): min intensity = " + min + ", max intensity = " + max );

		// normalize image
		FusionTools.normalizeImage( img.imglib2Img(), min, max, service );

		final float k = LaPlaceFunctions.computeK( 4 );
		final float K_MIN1_INV = LaPlaceFunctions.computeKWeight(k);
		final int steps = 3;
		
		//
		// Compute the Sigmas for the gaussian convolution
		//
		final float[] sigmaStepsX = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
		final float[] sigmaStepsDiffX = LaPlaceFunctions.computeSigmaDiff( sigmaStepsX, (float)imageSigmaX );
		
		final float[] sigmaStepsY = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
		final float[] sigmaStepsDiffY = LaPlaceFunctions.computeSigmaDiff( sigmaStepsY, (float)imageSigmaY );
		
		final float[] sigmaStepsZ = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
		final float[] sigmaStepsDiffZ = LaPlaceFunctions.computeSigmaDiff( sigmaStepsZ, (float)imageSigmaZ );
		
		final double[] sigma1 = new double[]{ sigmaStepsDiffX[0], sigmaStepsDiffY[0], sigmaStepsDiffZ[0] };
		final double[] sigma2 = new double[]{ sigmaStepsDiffX[1], sigmaStepsDiffY[1], sigmaStepsDiffZ[1] };

		// compute difference of gaussian
		DifferenceOfGaussianNewPeakFinder dog;
		
		if ( deviceList == null )
			dog = new DifferenceOfGaussianNewPeakFinder( img.imglib1Img(), new OutOfBoundsStrategyMirrorFactory<FloatType>(), sigma1, sigma2, minInitialPeakValue, K_MIN1_INV );
		else
			dog = new DifferenceOfGaussianCUDA( cuda, percentGPUMem, deviceList, img.imglib1Img(), img.imglib2Img(), accurateCUDA, sigma1, sigma2, minInitialPeakValue, K_MIN1_INV );

		dog.setComputeConvolutionsParalell( false );
		dog.setNumThreads( numThreads );

		// do quadratic fit??
		if ( localization == 1 )
			dog.setKeepDoGImage( true );
		else
			dog.setKeepDoGImage( false );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): computing difference-of-gaussian (sigma=" + initialSigma + ", " +
				"threshold=" + minPeakValue + ", sigma1=" + Util.printCoordinates( sigma1 ) + ", sigma2=" + Util.printCoordinates( sigma2 ) + ")" );

		if ( deviceList == null )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Computing DoG image (CPU)." );
		else
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Computing DoG image (GPU)." );

		dog.process();

		final ArrayList< SimplePeak > peaks = dog.getSimplePeaks();

		/*
		final ArrayList< SimplePeak > peaks = new ArrayList< SimplePeak >();
		final ArrayList< DifferenceOfGaussianPeak<FloatType> > peakListOld = dog.getPeaks();
		final int n = img.getNumDimensions();

		for ( final DifferenceOfGaussianPeak<FloatType> peak : peakListOld )
		{
			if ( peak.isValid() )
			{
				final int[] location = new int[ n ];

				for ( int d = 0; d < n; ++d )
					location[ d ] = peak.getPosition( d );

				peaks.add( new SimplePeak( location, peak.getValue().get(), peak.isMin(), peak.isMax() ) );
			}
		}
		*/

		final ArrayList< InterestPoint > finalPeaks;

		if ( localization == 0 )
		{
			finalPeaks = Localization.noLocalization( peaks, findMin, findMax, keepIntensity );
		}
		else if ( localization == 1 )
		{
			finalPeaks = Localization.computeQuadraticLocalization( peaks, dog.getDoGImage(), findMin, findMax, minPeakValue, keepIntensity, numThreads );
			dog.getDoGImage().close();
		}
		else
		{
			finalPeaks = Localization.computeGaussLocalization( peaks, null, sigma, findMin, findMax, minPeakValue, keepIntensity );
		}
		
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );

		// explicitly call the garbage collection as there are some outofmemory issues when processing many timepoints
		dog = null;
		System.gc();

		return finalPeaks;
	}
}
