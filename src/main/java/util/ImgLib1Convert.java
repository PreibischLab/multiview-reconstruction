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
package util;

import java.util.concurrent.ExecutorService;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib2;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class ImgLib1Convert
{
	/*
	 * ImgLib1 image (based on same image data as the ImgLib2 image, must be a wrap for DoG!)
	 */
	final Image< FloatType > imglib1Img;

	/*
	 * ImgLib2 image (based on same image data as the ImgLib1 image, must be a wrap for DoG!)
	 */
	final Img< net.imglib2.type.numeric.real.FloatType > imglib2Img;

	/*
	 * the min (offset) of the RandomAccessibleInterval that was passed into the class,
	 * necessary for correcting locations later on
	 */
	final long[] min;

	public < T extends RealType< T > > ImgLib1Convert( final RandomAccessibleInterval< T > input, final ExecutorService service )
	{
		this.min = new long[ input.numDimensions() ];
		input.min( min );

		final Pair< Image< FloatType >, Img< net.imglib2.type.numeric.real.FloatType > > images = 
				convertToImgLib1( input, service );

		this.imglib1Img = images.getA();
		this.imglib2Img = images.getB();
	}

	public Image< FloatType > imglib1Img() { return imglib1Img; }
	public Img< net.imglib2.type.numeric.real.FloatType > imglib2Img() { return imglib2Img; }
	public long[] min() { return min; }

	@SuppressWarnings("unchecked")
	public < T extends RealType< T > > Pair< Image< FloatType >, Img< net.imglib2.type.numeric.real.FloatType > > convertToImgLib1(
			final RandomAccessibleInterval< T > input,
			final ExecutorService service )
	{
		final T type = input.getAt( min );

		final RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > inputFloat;

		if ( net.imglib2.type.numeric.real.FloatType.class.isInstance( type ) )
			inputFloat = (RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType >)input;
		else
			inputFloat = ImgLib2Tools.convertVirtual( input );

		final Img< net.imglib2.type.numeric.real.FloatType > imglib2Img;

		if ( Img.class.isInstance( input ) )
		{
			imglib2Img = (Img< net.imglib2.type.numeric.real.FloatType >) inputFloat;
		}
		else
		{
			final long[] dim = new long[ input.numDimensions() ];
			imglib2Img = ArrayImgs.floats( dim );

			FusionTools.copyImg( Views.zeroMin( inputFloat ), imglib2Img, service );
		}

		final Image< FloatType > imglib1Img = ImgLib2.wrapFloatToImgLib1( imglib2Img );

		return new ValuePair<>( imglib1Img, imglib2Img );
	}
}
