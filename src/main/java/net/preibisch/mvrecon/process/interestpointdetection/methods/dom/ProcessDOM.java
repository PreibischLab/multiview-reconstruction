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
package net.preibisch.mvrecon.process.interestpointdetection.methods.dom;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.integer.LongType;
import mpicbg.imglib.type.numeric.real.FloatType;
import net.imglib2.img.Img;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.segmentation.DOM;
import net.preibisch.legacy.segmentation.IntegralImage3d;
import net.preibisch.legacy.segmentation.InteractiveIntegral;
import net.preibisch.legacy.segmentation.SimplePeak;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointdetection.Localization;

public class ProcessDOM 
{
	/*
	 * @param img - ImgLib1 image
	 * @param imglib2img - ImgLib2 image (based on same image data as the ImgLib1 image, must be a wrap)
	 * @param radius1
	 * @param radius2
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
			final Image< FloatType > img,
			final Img< net.imglib2.type.numeric.real.FloatType > imglib2img,
			final int radius1, 
			final int radius2, 
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
		final Image< LongType > integralImg = IntegralImage3d.compute( img );

		final float min, max;

		if ( Double.isNaN( minIntensity ) || Double.isNaN( maxIntensity ) || Double.isInfinite( minIntensity ) || Double.isInfinite( maxIntensity ) || minIntensity == maxIntensity )
		{
			final float[] minmax = FusionTools.minMax( imglib2img );
			min = minmax[ 0 ];
			max = minmax[ 1 ];
		}
		else
		{
			min = (float)minIntensity;
			max = (float)maxIntensity;
		}

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): min intensity = " + min + ", max intensity = " + max );
		
		// in-place
		final int sX1 = Math.max( 3, (int)Math.round( radius1 * (0.5/imageSigmaX ) ) * 2 + 1 );
		final int sX2 = Math.max( 5, (int)Math.round( radius2 * (0.5/imageSigmaX ) ) * 2 + 1 );

		final int sY1 = Math.max( 3, (int)Math.round( radius1 * (0.5/imageSigmaY ) ) * 2 + 1 );
		final int sY2 = Math.max( 5, (int)Math.round( radius2 * (0.5/imageSigmaY ) ) * 2 + 1 );

		final int sZ1 = Math.max( 3, (int)Math.round( radius1 * (0.5/imageSigmaZ ) ) * 2 + 1 );
		final int sZ2 = Math.max( 5, (int)Math.round( radius2 * (0.5/imageSigmaZ ) ) * 2 + 1 );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Computing Difference-of-Mean, diameters = (" + sX1 + ", "  + sX2 + ", "  + sY1 + ", "  + sY2 + ", "  + sZ1 + ", "  + sZ2 + ")" );

		// in-place overwriting img if no adjacent Gauss fit is required
		final Image< FloatType > domImg;
		
		if ( localization == 2 )
		{
			domImg = img.createNewImage();
		}
		else
		{
			domImg = img;
			for ( final FloatType tt : img )
				tt.setZero();
		}
		
		DOM.computeDifferencOfMean3d( integralImg, domImg, sX1, sY1, sZ1, sX2, sY2, sZ2, min, max );

		// close integral img
		integralImg.close();
		
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Extracting peaks (radius=" + radius1 + ", threshold=" + threshold + ")");					

		// compute the maxima/minima
		final ArrayList< SimplePeak > peaks = InteractiveIntegral.findPeaks( domImg, threshold );
		final ArrayList< InterestPoint > finalPeaks;
		
		if ( localization == 0 )
			finalPeaks = Localization.noLocalization( peaks, findMin, findMax, keepIntensity );
		else if ( localization == 1 )
			finalPeaks = Localization.computeQuadraticLocalization( peaks, domImg, findMin, findMax, threshold, keepIntensity, Threads.numThreads() );
		else
			finalPeaks = Localization.computeGaussLocalization( peaks, domImg, ( radius2 + radius1 )/2.0, findMin, findMax, threshold, keepIntensity );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );
		
		return finalPeaks;
	}
}
