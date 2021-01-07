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

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import ij.IJ;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import util.ImgLib1Convert;

public class DoG
{
	final DoGParameters dog;

	public DoG( final DoGParameters dog )
	{
		this.dog = dog;
	}

	public static HashMap< ViewId, List< InterestPoint >> findInterestPoints( final DoGParameters dog )
	{
		final HashMap< ViewId, List< InterestPoint >> interestPoints = new HashMap< ViewId, List< InterestPoint >>();

		addInterestPoints( interestPoints, dog );

		return interestPoints;
	}

	/**
	 * finds all interest points and returns them as InterestPoints (which is a mpicbg Point and implements RealLocalizable),
	 * by default returns world coordinates
	 *
	 * more parameters are omitted
	 *
	 * @param input - any RandomAccessibleInterval (Img will be casted only otherwise copied), non-FloatType will be converted, everything is normalized to 0...1 for processing
	 * @param sigma - sigma for the DoG detection (try InteractiveDoG to figure out the right parameters)
	 * @param threshold - threshold for the DoG detection (try InteractiveDoG to figure out the right parameters)
	 * @param findMin - find intensity minima
	 * @param findMax - fina intensity maxima
	 * @param minIntensity - the min intensity for normalization to 0...1, if Double.NaN the value will be looked up
	 * @param maxIntensity - the max intensity for normalization to 0...1, if Double.NaN the value will be looked up
	 * @param numThreads - number of threads to use
	 *
	 * @return a list of interest points
	 */
	public static < T extends RealType< T > & NativeType< T > > List< InterestPoint > findInterestPoints(
			final RandomAccessibleInterval< T > input,
			final double sigma,
			final double threshold,
			final boolean findMin,
			final boolean findMax,
			final double minIntensity,
			final double maxIntensity,
			final int numThreads )
	{
		final ExecutorService service = Threads.createFixedExecutorService( numThreads );

		//
		// compute Difference-of-Gaussian (includes normalization)
		//
		List< InterestPoint > ips = DoGImgLib2.computeDoG(
				input,
				null,
				sigma,
				threshold,
				1, // 0 = no subpixel localization, 1 = quadratic fit
				findMin,
				findMax,
				minIntensity,
				maxIntensity,
				service,
				numThreads );

		//if ( dog.limitDetections )
		//	ips = InterestPointTools.limitList( dog.maxDetections, dog.maxDetectionsTypeIndex, ips );

		// adjust detections for min coordinates of the RandomAccessibleInterval
		for ( final InterestPoint ip : ips )
		{
			for ( int d = 0; d < input.numDimensions(); ++d )
			{
				ip.getL()[ d ] += input.min( d );
				ip.getW()[ d ] += input.min( d );
			}
		}

		return ips;
	}

	public static void addInterestPoints( final HashMap< ViewId, List< InterestPoint > > interestPoints, final DoGParameters dog )
	{
		if ( dog.showProgress() )
			IJ.showProgress( dog.showProgressMin );

		int count = 1;

		// TODO: special iterator that takes into account missing views
		for ( final ViewDescription vd : dog.toProcess )
		{
			// make sure not everything crashes if one file is missing
			try
			{
				//
				// open the corresponding image (if present at this timepoint)
				//
				if ( !vd.isPresent() )
					continue;

				final AffineTransform3D correctCoordinates = new AffineTransform3D();

				final ExecutorService service = Threads.createFixedExecutorService( Threads.numThreads() );

				@SuppressWarnings("unchecked")
				final RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input =
						DownsampleTools.openAndDownsample(
								dog.imgloader,
								vd,
								correctCoordinates,
								new long[] { dog.downsampleXY, dog.downsampleXY, dog.downsampleZ },
								false,  //transformOnly
								false,   //openAsFloat
								false, //openCompletely
								service );

				List< InterestPoint > ips;

				if ( dog.cuda == null )
				{
					ips = DoGImgLib2.computeDoG(input, null, dog.sigma, dog.threshold, dog.localization, dog.findMin, dog.findMax, dog.minIntensity,
						dog.maxIntensity, service, Threads.numThreads() );
				}
				else
				{
					
					final ImgLib1Convert convert = new ImgLib1Convert( input, service );
	
					//
					// compute Difference-of-Gaussian (includes normalization)
					//
					ips = ProcessDOG.compute(
							dog.cuda, dog.deviceList, dog.accurateCUDA, dog.percentGPUMem,
							service,
							Threads.numThreads(),
							convert,
							(float) dog.sigma, (float) dog.threshold,
							dog.localization,
							Math.min( dog.imageSigmaX, (float) dog.sigma ),
							Math.min( dog.imageSigmaY, (float) dog.sigma ),
							Math.min( dog.imageSigmaZ, (float) dog.sigma ),
							dog.findMin, dog.findMax, dog.minIntensity,
							dog.maxIntensity,
							dog.limitDetections );
	
					convert.imglib1Img().close();
				}

				service.shutdown();

				if ( dog.limitDetections )
					ips = InterestPointTools.limitList( dog.maxDetections, dog.maxDetectionsTypeIndex, ips );

				DownsampleTools.correctForDownsampling( ips, correctCoordinates );

				interestPoints.put( vd, ips );
			} catch ( Exception e )
			{
				IOFunctions.println( "An error occured (DOG): " + e );
				IOFunctions.println( "Failed to segment angleId: "
						+ vd.getViewSetup().getAngle().getId() + " channelId: "
						+ vd.getViewSetup().getChannel().getId() + " illumId: "
						+ vd.getViewSetup().getIllumination().getId()
						+ ". Continuing with next one." );
				e.printStackTrace();
			}

			if ( dog.showProgress() )
				IJ.showProgress( dog.showProgressMin + 
						( (double)(count++) / (double)dog.toProcess.size() ) / ( dog.showProgressMax - dog.showProgressMin ) );
		}

		if ( dog.showProgress() )
			IJ.showProgress( dog.showProgressMax );
	}
}
