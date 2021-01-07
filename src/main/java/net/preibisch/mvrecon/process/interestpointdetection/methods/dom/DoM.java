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

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib2;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.downsampling.DownsampleTools;

/**
 * Created by schmied on 01/07/15.
 */
public class DoM
{
	final DoMParameters dom;

	public DoM( final DoMParameters dom )
	{
		this.dom = dom;
	}

	public static HashMap< ViewId, List< InterestPoint > > findInterestPoints( final DoMParameters dom )
	{
		final HashMap< ViewId, List< InterestPoint >> interestPoints = new HashMap< ViewId, List< InterestPoint >>();

		addInterestPoints( interestPoints, dom );

		return interestPoints;
	}

	public static void addInterestPoints( final HashMap< ViewId, List< InterestPoint >> interestPoints, final DoMParameters dom )
	{
		// TODO: special iterator that takes into account missing views
		// make sure not everything crashes if one file is missing
		for ( final ViewDescription vd : dom.toProcess )
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
						dom.imgloader,
						vd,
						correctCoordinates,
						new long[] { dom.downsampleXY, dom.downsampleXY, dom.downsampleZ },
						false,  //transformOnly
						true,   //openAsFloat
						true, //openCompletely
						service );

				service.shutdown();

				final Image< FloatType > img = ImgLib2.wrapFloatToImgLib1(
						(Img< net.imglib2.type.numeric.real.FloatType >) input );

				// Compute DifferenceOfMean
				List< InterestPoint > ips = ProcessDOM.compute(
						img,
						(Img< net.imglib2.type.numeric.real.FloatType >) input,
						dom.radius1,
						dom.radius2,
						dom.threshold,
						dom.localization,
						dom.imageSigmaX,
						dom.imageSigmaY,
						dom.imageSigmaZ,
						dom.findMin,
						dom.findMax,
						dom.minIntensity,
						dom.maxIntensity,
						dom.limitDetections );

				img.close();

				if ( dom.limitDetections )
					ips = InterestPointTools.limitList( dom.maxDetections, dom.maxDetectionsTypeIndex, ips );

				DownsampleTools.correctForDownsampling( ips, correctCoordinates );

				interestPoints.put( vd, ips );
			}
			catch ( Exception e )
			{
				IOFunctions.println( "An error occured (Difference of Mean): " + e );
				IOFunctions.println( "Failed to segment angleId: "
						+ vd.getViewSetup().getAngle().getId() + " channelId: "
						+ vd.getViewSetup().getChannel().getId() + " illumId: "
						+ vd.getViewSetup().getIllumination().getId()
						+ ". Continuing with next one." );
				e.printStackTrace();
			}
		}
	}
}
