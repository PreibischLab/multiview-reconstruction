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
package net.preibisch.mvrecon.headless.quality;

import java.io.File;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.quality.FRC;
import net.preibisch.mvrecon.process.quality.FRC.ThresholdMethod;

public class FRCTest
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		//final Img< FloatType > img = IOFunctions.openAs32BitArrayImg( new File( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/spim_TL18_Angle0.tif" ) );
		final Img< FloatType > img = IOFunctions.openAs32BitArrayImg( new File(
				"/Users/spreibi/Documents/BIMSB/Projects/CLARITY/Quality assessment/brain-A.tif" ) );
		final ImagePlus imp = DisplayImage.getImagePlusInstance( img, true, "brain", Double.NaN, Double.NaN );
		imp.show();

		testFRC( img );
	}

	public static void testFRC( final Img< FloatType > img )
	{
		ThresholdMethod tm = ThresholdMethod.FIXED_1_OVER_7;
		ImageStack stack = null;

		for ( int z = 0; z < img.dimension( 2 ) - 1; z ++ )
		{
			//Views.interval( randomAccessible, new long[]], max )
			final RandomAccessibleInterval< FloatType > s0 = Views.hyperSlice( img, 2, z );
			final RandomAccessibleInterval< FloatType > s1 = Views.hyperSlice( img, 2, z + 1 );

			final FloatProcessor fp0 = new FloatProcessor( (int)s0.dimension( 0 ), (int)s0.dimension( 1 ) );
			final FloatProcessor fp1 = new FloatProcessor( (int)s0.dimension( 0 ), (int)s0.dimension( 1 ) );

			final Cursor< FloatType > c0 = Views.iterable( s0 ).localizingCursor();
			final Cursor< FloatType > c1 = Views.iterable( s1 ).localizingCursor();

			while ( c0.hasNext() )
			{
				c0.fwd();
				c1.fwd();

				fp0.setf( c0.getIntPosition( 0 ), c0.getIntPosition( 1 ), c0.get().get() );
				fp1.setf( c1.getIntPosition( 0 ), c1.getIntPosition( 1 ), c1.get().get() );
			}

			FRC frc = new FRC();
			 
			// Get FIRE Number, assumes you have access to the two image processors.
			double[][] frcCurve = frc.calculateFrcCurve(fp0, fp1);
			
			double fire = frc.calculateFireNumber( frcCurve, tm );
			System.out.println( z + ": " + fire );

			//if ( z== 41 || z== 42 )
			{
			Plot p = frc.doPlot( frcCurve, frc.getSmoothedCurve( frcCurve ), tm, fire, "" + z );
			ImageProcessor ip = p.getImagePlus().getProcessor();
			if ( stack == null )
				stack = new ImageStack( ip.getWidth(), ip.getHeight() );
			stack.addSlice( ip );
			//p.show();
			}

			
			
			//break;
		}
		
		new ImagePlus( "fd", stack ).show();
	}
}
