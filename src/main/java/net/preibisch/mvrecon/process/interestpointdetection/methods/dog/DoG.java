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
package net.preibisch.mvrecon.process.interestpointdetection.methods.dog;

import java.util.HashMap;
import java.util.List;

import ij.IJ;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib2;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;

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
				final RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input =
						DownsampleTools.openAndDownsample(
								dog.imgloader,
								vd,
								correctCoordinates,
								dog.downsampleXY,
								dog.downsampleZ,
								true );

				final Image< FloatType > img = ImgLib2
						.wrapFloatToImgLib1( (Img< net.imglib2.type.numeric.real.FloatType >) input );

				//
				// compute Difference-of-Gaussian
				//
				List< InterestPoint > ips = ProcessDOG.compute(
						dog.cuda, dog.deviceList, dog.accurateCUDA, dog.percentGPUMem,
						img,
						(Img< net.imglib2.type.numeric.real.FloatType >) input,
						(float) dog.sigma, (float) dog.threshold,
						dog.localization,
						Math.min( dog.imageSigmaX, (float) dog.sigma ),
						Math.min( dog.imageSigmaY, (float) dog.sigma ),
						Math.min( dog.imageSigmaZ, (float) dog.sigma ),
						dog.findMin, dog.findMax, dog.minIntensity,
						dog.maxIntensity,
						dog.limitDetections );

				img.close();

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
