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
package net.preibisch.mvrecon.headless.fusion;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import bdv.util.ConstantRandomAccessible;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LegacyStackImgLoaderIJ;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.ImagePortion;

public class HustonFusion
{
	public static void main( String[] args )
	{
		new ImageJ();
		String dir = "/groups/scicompsoft/home/preibischs/Desktop/huston";

		fuseAdvanced( dir );
	}

	public static void fuseAdvanced( final String dir )
	{
		final Img< UnsignedByteType > mask1 = load( new File( dir, "mask_tp_0_vs_1.tif" ) );
		final Img< UnsignedByteType > mask2 = load( new File( dir, "mask_tp_0_vs_2.tif" ) );

		final Img< UnsignedByteType > vs0 = load( new File( dir, "AFFINE_fused_tp_0_vs_0.tif" ) );
		final Img< UnsignedByteType > vs1 = load( new File( dir, "AFFINE_fused_tp_0_vs_1.tif" ) );
		final Img< UnsignedByteType > vs2 = load( new File( dir, "AFFINE_fused_tp_0_vs_2.tif" ) );

		final RandomAccessibleInterval< BitType > weight0 = Views.interval( new ConstantRandomAccessible<BitType>( new BitType( true ), vs0.numDimensions() ), vs0 );
		final Img< BitType > weight1 = new ImagePlusImgFactory<BitType>( new BitType() ).create( vs0 );
		final Img< BitType > weight2 = new ImagePlusImgFactory<BitType>( new BitType() ).create( vs0 );

		ImageJFunctions.show( weight0 );
		SimpleMultiThreading.threadHaltUnClean();
		
		final long numPixels = Views.iterable( vs0 ).size();
		final int nThreads = Threads.numThreads();
		final int dist = 100;


		final Img< UnsignedByteType > img = new ImagePlusImgFactory<UnsignedByteType>( new UnsignedByteType() ).create( vs0 );

		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( numPixels );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		final AtomicInteger progress = new AtomicInteger( 0 );

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					fuseSimple( portion.getStartPosition(), portion.getLoopSize(), vs0, vs1, vs2, mask1, mask2, img, dist );

					IJ.showProgress( (double)progress.incrementAndGet() / tasks.size() );

					return null;
				}
			});
		}

		IJ.showProgress( 0.01 );

		FusionTools.execTasks( tasks, nThreads, "fuse image" );

		((ImagePlusImg)img).getImagePlus().show();
	}

	public static <T extends RealType< T >>void computeWeightImage(
			final long start,
			final long loopSize,
			final Img< UnsignedByteType > vs,
			final Img< UnsignedByteType > mask,
			final Img< T > weight,
			final int dist,
			final int imageId )
	{
		if ( imageId == 0 )
			throw new RuntimeException( "id=0 is always one" );

		final Cursor<T> cW = weight.cursor();
		final Cursor<UnsignedByteType> cV = vs.cursor();

		cW.jumpFwd( start );
		cV.jumpFwd( start );

		final RandomAccess< UnsignedByteType > r = Views.extendZero(vs).randomAccess();

		final RandomAccess< UnsignedByteType > m = mask.randomAccess();

		for ( long l = 0; l < loopSize; ++l )
		{
			int v = cV.next().get();

			m.setPosition(cV.getIntPosition(0), 0);
			m.setPosition(cV.getIntPosition(1), 1);
			if ( m.get().get() > 0 && ( v > 0 || !isOutside( cV, r, dist ) ) )
			{
				cW.next().setOne();
			}
			else
			{
				cW.next().setZero();
			}
		}
	}

	public static void fuseSimple( final String dir )
	{
		final Img< UnsignedByteType > mask1 = load( new File( dir, "mask_tp_0_vs_1.tif" ) );
		final Img< UnsignedByteType > mask2 = load( new File( dir, "mask_tp_0_vs_2.tif" ) );

		final Img< UnsignedByteType > vs0 = load( new File( dir, "AFFINE_fused_tp_0_vs_0.tif" ) );
		final Img< UnsignedByteType > vs1 = load( new File( dir, "AFFINE_fused_tp_0_vs_1.tif" ) );
		final Img< UnsignedByteType > vs2 = load( new File( dir, "AFFINE_fused_tp_0_vs_2.tif" ) );

		final Img< UnsignedByteType > img = new ImagePlusImgFactory<UnsignedByteType>( new UnsignedByteType() ).create( vs0 );

		final int dist = 100;

		final long numPixels = Views.iterable( img ).size();
		final int nThreads = Threads.numThreads();
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( numPixels );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		final AtomicInteger progress = new AtomicInteger( 0 );

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					fuseSimple( portion.getStartPosition(), portion.getLoopSize(), vs0, vs1, vs2, mask1, mask2, img, dist );

					IJ.showProgress( (double)progress.incrementAndGet() / tasks.size() );

					return null;
				}
			});
		}

		IJ.showProgress( 0.01 );

		FusionTools.execTasks( tasks, nThreads, "fuse image" );

		((ImagePlusImg)img).getImagePlus().show();
	}

	private static final void fuseSimple(
			final long start,
			final long loopSize,
			final Img< UnsignedByteType > vs0,
			final Img< UnsignedByteType > vs1,
			final Img< UnsignedByteType > vs2,
			final Img< UnsignedByteType > mask1,
			final Img< UnsignedByteType > mask2,
			final Img< UnsignedByteType > img,
			final int dist )
	{
		final Cursor<UnsignedByteType> i0 = img.cursor();
		final Cursor<UnsignedByteType> c0 = vs0.cursor();
		final Cursor<UnsignedByteType> c1 = vs1.cursor();
		final Cursor<UnsignedByteType> c2 = vs2.cursor();

		c0.jumpFwd( start );
		c1.jumpFwd( start );
		c2.jumpFwd( start );
		i0.jumpFwd( start );

		final RandomAccess< UnsignedByteType > r0 = Views.extendZero(vs0).randomAccess();
		final RandomAccess< UnsignedByteType > r1 = Views.extendZero(vs1).randomAccess();
		final RandomAccess< UnsignedByteType > r2 = Views.extendZero(vs2).randomAccess();

		final RandomAccess< UnsignedByteType > m1 = mask1.randomAccess();
		final RandomAccess< UnsignedByteType > m2 = mask2.randomAccess();

		for ( long l = 0; l < loopSize; ++l )
		{
			int v0 = c0.next().get();
			int v1 = c1.next().get();
			int v2 = c2.next().get();

			long avg = 0;
			int count = 0;

			
			//if ( v0 > 0 || !isOutside( c0, r0, dist ) )
			{
				avg += v0;
				++count;
			}

			m1.setPosition(c0.getIntPosition(0), 0);
			m1.setPosition(c0.getIntPosition(1), 1);
			if ( m1.get().get() > 0 && ( v1 > 0 || !isOutside( c1, r1, dist ) ) )
			{
				avg += v1;
				++count;
			}

			m2.setPosition(c0.getIntPosition(0), 0);
			m2.setPosition(c0.getIntPosition(1), 1);
			if ( m2.get().get() > 0 && ( v2 > 0 || !isOutside( c2, r2, dist ) ) )
			{
				avg += v2;
				++count;
			}

			if ( count > 0 )
				i0.next().set( Math.round( (float)avg/(float)count ) );
			else
				i0.fwd();
		}		
	}
	
	private static final boolean isOutside( final Cursor<UnsignedByteType> c, final RandomAccess< UnsignedByteType > r, final int dist )
	{
		r.setPosition(c);
		boolean allZero = true;
		for ( int x = 0; x < dist; ++x )
		{
			r.fwd( 0 );
			if ( r.get().get() != 0 )
			{
				allZero = false;
				break;
			}
		}

		if ( allZero )
			return true;

		r.setPosition(c);
		allZero = true;
		for ( int x = 0; x < dist; ++x )
		{
			r.bck( 0 );
			if ( r.get().get() != 0 )
			{
				allZero = false;
				break;
			}
		}

		if ( allZero )
			return true;

		r.setPosition(c);
		allZero = true;
		for ( int x = 0; x < dist; ++x )
		{
			r.fwd( 1 );
			if ( r.get().get() != 0 )
			{
				allZero = false;
				break;
			}
		}

		if ( allZero )
			return true;

		r.setPosition(c);
		allZero = true;
		for ( int x = 0; x < dist; ++x )
		{
			r.bck( 1 );
			if ( r.get().get() != 0 )
			{
				allZero = false;
				break;
			}
		}

		if ( allZero )
			return true;

		r.setPosition(c);
		allZero = true;
		for ( int x = 0; x < dist; ++x )
		{
			r.fwd( 2 );
			if ( r.get().get() != 0 )
			{
				allZero = false;
				break;
			}
		}

		if ( allZero )
			return true;

		r.setPosition(c);
		allZero = true;
		for ( int x = 0; x < dist; ++x )
		{
			r.bck( 2 );
			if ( r.get().get() != 0 )
			{
				allZero = false;
				break;
			}
		}

		if ( allZero )
			return true;
		else
			return false;
	}

	public static Img< UnsignedByteType > load( final File file )
	{
		final ImagePlus imp = LegacyStackImgLoaderIJ.open( file );
		return ImageJFunctions.wrap(imp);
		
		/*
		final long[] dim = new long[]{ imp.getWidth(), imp.getHeight(), imp.getStack().getSize() };
		final Img< UnsignedByteType > img = new ImagePlusImgFactory<UnsignedByteType>( new UnsignedByteType() ).create( dim );
		
		final ImageStack stack = imp.getStack();
		final int sizeZ = imp.getStack().getSize();
		final Cursor< UnsignedByteType > cursor = img.cursor();
		final int sizeXY = imp.getWidth() * imp.getHeight();

		for ( int z = 0; z < sizeZ; ++z )
		{
			final ImageProcessor ip = stack.getProcessor( z + 1 );

			for ( int i = 0; i < sizeXY; ++i )
				cursor.next().set( ip.get( i ) );
		}

		return img;
		*/
	}
}
