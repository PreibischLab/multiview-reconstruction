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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mpicbg.imglib.container.array.ArrayContainerFactory;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import net.imglib2.RandomAccess;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.view.Views;
import net.preibisch.legacy.registration.detection.DetectionSegmentation;
import net.preibisch.mvrecon.process.fusion.FusionTools;

/**
 * An interactive tool for determining the required sigma and peak threshold
 * 
 * @author Stephan Preibisch, Marwan Zouinkhi
 */
public class DoG {
	
	ImagePlus imp;
	
	public static float computeSigma2( final float sigma1, final int sensitivity )
	{
        final float k = (float)DetectionSegmentation.computeK( sensitivity );
        final float[] sigma = DetectionSegmentation.computeSigma( k, sigma1 );
        
        return sigma[ 1 ];
	}
	
	/**
	 * Extract the current 2d region of interest from the souce image
	 * 
	 * @param source - the source image, a {@link Image} which is a copy of the {@link ImagePlus}
	 * @param rectangle - the area of interest
	 * @param extraSize - the extra size around so that detections at the border of the roi are not messed up
	 * @return the extracted image
	 */
	protected Image<FloatType> extractImage( final FloatImagePlus< net.imglib2.type.numeric.real.FloatType > source, final Rectangle rectangle, final int extraSize )
	{
		final Image<FloatType> img = new ImageFactory<FloatType>( new FloatType(), new ArrayContainerFactory() ).createImage( new int[]{ rectangle.width+extraSize, rectangle.height+extraSize } );
		
		final int offsetX = rectangle.x - extraSize/2;
		final int offsetY = rectangle.y - extraSize/2;

		final int[] location = new int[ source.numDimensions() ];
		
		if ( location.length > 2 )
			location[ 2 ] = (imp.getCurrentSlice()-1)/imp.getNChannels();
				
		final LocalizableCursor<FloatType> cursor = img.createLocalizableCursor();
		final RandomAccess<net.imglib2.type.numeric.real.FloatType> positionable;
		
		if ( offsetX >= 0 && offsetY >= 0 && 
			 offsetX + img.getDimension( 0 ) < source.dimension( 0 ) && 
			 offsetY + img.getDimension( 1 ) < source.dimension( 1 ) )
		{
			// it is completely inside so we need no outofbounds for copying
			positionable = source.randomAccess();
		}
		else
		{
			positionable = Views.extendMirrorSingle( source ).randomAccess();
		}
			
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.getPosition( location );
			
			location[ 0 ] += offsetX;
			location[ 1 ] += offsetY;
			
			positionable.setPosition( location );
			
			cursor.getType().set( positionable.get().get() );
		}
		
		return img;
	}
	
	/**
	 * Normalize and make a copy of the {@link ImagePlus} into an {@link Image}&gt;FloatType&lt; for faster access when copying the slices
	 * 
	 * @param imp - the {@link ImagePlus} input image
	 * @param channel - channel
	 * @param timepoint - timepoint
	 * @return - the normalized copy [0...1]
	 */
	public static FloatImagePlus< net.imglib2.type.numeric.real.FloatType > convertToFloat( final ImagePlus imp, int channel, int timepoint )
	{
		return convertToFloat( imp, channel, timepoint, Double.NaN, Double.NaN );
	}

	public static FloatImagePlus< net.imglib2.type.numeric.real.FloatType > convertToFloat( final ImagePlus imp, int channel, int timepoint, final double min, final double max )
	{
		// stupid 1-offset of imagej
		channel++;
		timepoint++;

		final int h = imp.getHeight();
		final int w = imp.getWidth();

		final ArrayList< float[] > img = new ArrayList< float[] >();

		if ( imp.getProcessor() instanceof FloatProcessor )
		{
			for ( int z = 0; z < imp.getNSlices(); ++z )
				img.add( ( (float[])imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) ).getPixels() ).clone() );
		}
		else if ( imp.getProcessor() instanceof ByteProcessor )
		{
			for ( int z = 0; z < imp.getNSlices(); ++z )
			{
				final byte[] pixels = (byte[])imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) ).getPixels();
				final float[] pixelsF = new float[ pixels.length ];

				for ( int i = 0; i < pixels.length; ++i )
					pixelsF[ i ] = pixels[ i ] & 0xff;

				img.add( pixelsF );
			}
		}
		else if ( imp.getProcessor() instanceof ShortProcessor )
		{
			for ( int z = 0; z < imp.getNSlices(); ++z )
			{
				final short[] pixels = (short[])imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) ).getPixels();
				final float[] pixelsF = new float[ pixels.length ];

				for ( int i = 0; i < pixels.length; ++i )
					pixelsF[ i ] = pixels[ i ] & 0xffff;

				img.add( pixelsF );
			}
		}
		else // some color stuff or so 
		{
			for ( int z = 0; z < imp.getNSlices(); ++z )
			{
				final ImageProcessor ip = imp.getStack().getProcessor( imp.getStackIndex( channel, z + 1, timepoint ) );
				final float[] pixelsF = new float[ w * h ];

				int i = 0;

				for ( int y = 0; y < h; ++y )
					for ( int x = 0; x < w; ++x )
						pixelsF[ i++ ] = ip.getPixelValue( x, y );

				img.add( pixelsF );
			}
		}

		final FloatImagePlus< net.imglib2.type.numeric.real.FloatType > i = createImgLib2( img, w, h );

		if ( Double.isNaN( min ) || Double.isNaN( max ) || Double.isInfinite( min ) || Double.isInfinite( max ) || min == max )
			FusionTools.normalizeImage( i );
		else
			FusionTools.normalizeImage( i, (float)min, (float)max );

		return i;
	}
	
	public static FloatImagePlus< net.imglib2.type.numeric.real.FloatType > createImgLib2( final List< float[] > img, final int w, final int h )
	{
		final ImagePlus imp;

		if ( img.size() > 1 )
		{
			final ImageStack stack = new ImageStack( w, h );
			for ( int z = 0; z < img.size(); ++z )
				stack.addSlice( new FloatProcessor( w, h, img.get( z ) ) );
			imp = new ImagePlus( "ImgLib2 FloatImagePlus (3d)", stack );
		}
		else
		{
			imp = new ImagePlus( "ImgLib2 FloatImagePlus (2d)", new FloatProcessor( w, h, img.get( 0 ) ) );
		}

		return ImagePlusAdapter.wrapFloat( imp );
	}
}
