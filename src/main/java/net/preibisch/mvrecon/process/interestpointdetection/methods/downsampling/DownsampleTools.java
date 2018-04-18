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
package net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

public class DownsampleTools
{
	protected static final int[] ds = { 1, 2, 4, 8 };

	/**
	 * Opens the image at an appropriate resolution for the provided transformation and concatenates an extra transform 
	 * 
	 * @param imgLoader - the img loader
	 * @param viewId - the view id
	 * @param m - WILL BE MODIFIED IF OPENED DOWNSAMPLED
	 * @return - opened image
	 */
	public static RandomAccessibleInterval openDownsampled( final BasicImgLoader imgLoader, final ViewId viewId, final AffineTransform3D m )
	{
		return openDownsampled( imgLoader, viewId, m, null );
	}

	/**
	 * Opens the image at an appropriate resolution for the provided transformation and concatenates an extra transform 
	 * 
	 * @param imgLoader - the img loader
	 * @param viewId - the view id
	 * @param m - WILL BE MODIFIED IF OPENED DOWNSAMPLED
	 * @param usedDownsampleFactors - which downsample factors were used to open the image (important for weights etc)
	 * @return - opened image
	 */
	public static RandomAccessibleInterval openDownsampled( final BasicImgLoader imgLoader, final ViewId viewId, final AffineTransform3D m, final double[] usedDownsampleFactors )
	{
		// have to go from input to output
		// https://github.com/bigdataviewer/bigdataviewer-core/blob/master/src/main/java/bdv/util/MipmapTransforms.java

		// pre-init downsample factors
		if ( usedDownsampleFactors != null )
			for ( int d = 0; d < usedDownsampleFactors.length; ++d )
				usedDownsampleFactors[ d ] = 1.0;

		if ( MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			final MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader )imgLoader;
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getMipmapResolutions();

			// best possible step size in the output image when using original data
			final float[] sizeMaxResolution = getStepSize( m );

			System.out.println( Util.printCoordinates( sizeMaxResolution ) );
			float acceptedError = 0.02f;

			// assuming that this is the best one
			int bestLevel = 0;
			double bestScaling = 0;

			// find the best level
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				final double[] factors = mipmapResolutions[ level ];

				final AffineTransform3D s = new AffineTransform3D();
				s.set(
					factors[ 0 ], 0.0, 0.0, 0.0,
					0.0, factors[ 1 ], 0.0, 0.0,
					0.0, 0.0, factors[ 2 ], 0.0 );
	
				System.out.println( "testing scale: " + s );
	
				AffineTransform3D model = m.copy();
				model.concatenate( s );

				final float[] size = getStepSize( model );

				boolean isValid = true;
				
				for ( int d = 0; d < 3; ++d )
					if ( !( size[ d ] < 1.0 + acceptedError || Util.isApproxEqual( size[ d ], sizeMaxResolution[ d ], acceptedError ) ) )
						isValid = false;

				if ( isValid )
				{
					final double totalScale = factors[ 0 ] * factors[ 1 ] * factors[ 2 ];
					
					if ( totalScale > bestScaling )
					{
						bestScaling = totalScale;
						bestLevel = level;
					}
				}
				System.out.println( Util.printCoordinates( size ) + " valid: " + isValid + " bestScaling: " + bestScaling  );
			}

			// concatenate the downsampling transformation model to the affine transform
			m.concatenate( mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );

			System.out.println( "Choosing resolution level: " + mipmapResolutions[ bestLevel ][ 0 ] + " x " + mipmapResolutions[ bestLevel ][ 1 ] + " x " + mipmapResolutions[ bestLevel ][ 2 ] );

			if ( usedDownsampleFactors != null && usedDownsampleFactors.length == mipmapResolutions[ bestLevel ].length )
				for ( int d = 0; d < usedDownsampleFactors.length; ++d )
					usedDownsampleFactors[ d ] = mipmapResolutions[ bestLevel ][ d ];

			return mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId(), bestLevel );
		}
		else
		{
			return imgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );
		}
	}

	private static float[] getStepSize( final AffineTransform3D model )
	{
		final float[] size = new float[ 3 ];

		final double[] tmp = new double[ 3 ];
		final double[] o0 = new double[ 3 ];

		model.apply( tmp, o0 );

		for ( int d = 0; d < 3; ++d )
		{
			final double[] o1 = new double[ 3 ];

			for ( int i = 0; i < tmp.length; ++i )
				tmp[ i ] = 0;

			tmp[ d ] = 1;

			model.apply( tmp, o1 );
			
			size[ d ] = (float)length( o1, o0 );
		}

		return size;
	}

	private static double length( final double[] a, final double[] b )
	{
		double l = 0;

		for ( int j = 0; j < a.length; ++j )
			l += ( a[ j ] - b[ j ] ) * ( a[ j ] - b[ j ] );

		return Math.sqrt( l );
	}

	/**
	 * For double-based downsampling Double.NaN means no downsampling to avoid unnecessary computations, here we return a String for that number that
	 * says "None" if it is Double.NaN
	 * 
	 * @param downsampling - the downsampling, Double.NaN means 1.0
	 * @return - a String describing it
	 */
	public static String printDownsampling( final double downsampling )
	{
		if ( Double.isNaN( downsampling ) )
			return "None";
		else
			return Double.toString( downsampling );
	}

	public static String[] availableDownsamplings( final AbstractSpimData< ? > data, final ViewId viewId )
	{
		final String[] dsStrings;

		if (MultiResolutionImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ))
		{
			final MultiResolutionImgLoader mrImgLoader = (MultiResolutionImgLoader) data.getSequenceDescription().getImgLoader();
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( viewId.getViewSetupId()).getMipmapResolutions();
			dsStrings = new String[mipmapResolutions.length];
			
			for (int i = 0; i<mipmapResolutions.length; i++)
			{
				final String fx = ((Long)Math.round( mipmapResolutions[i][0] )).toString(); 
				final String fy = ((Long)Math.round( mipmapResolutions[i][1] )).toString(); 
				final String fz = ((Long)Math.round( mipmapResolutions[i][2] )).toString();
				final String dsString = String.join( ", ", fx, fy, fz );
				dsStrings[i] = dsString;
			}
		}
		else
		{
			dsStrings = new String[]{ "1, 1, 1" };
		}

		return dsStrings;
	}

	public static long[] parseDownsampleChoice( final String dsChoice )
	{
		final long[] downSamplingFactors = new long[ 3 ];
		final String[] choiceSplit = dsChoice.split( ", " );
		downSamplingFactors[0] = Long.parseLong( choiceSplit[0] );
		downSamplingFactors[1] = Long.parseLong( choiceSplit[1] );
		downSamplingFactors[2] = Long.parseLong( choiceSplit[2] );
		
		return downSamplingFactors;
	}
	public static void correctForDownsampling( final List< InterestPoint > ips, final AffineTransform3D t )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Correcting coordinates for downsampling using AffineTransform: " + t );

		if ( ips == null || ips.size() == 0 )
		{
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): WARNING: List is empty." );
			return;
		}

		final double[] tmp = new double[ ips.get( 0 ).getL().length ];

		for ( final InterestPoint ip : ips )
		{
			t.apply( ip.getL(), tmp );

			ip.getL()[ 0 ] = tmp[ 0 ];
			ip.getL()[ 1 ] = tmp[ 1 ];
			ip.getL()[ 2 ] = tmp[ 2 ];

			t.apply( ip.getW(), tmp );

			ip.getW()[ 0 ] = tmp[ 0 ];
			ip.getW()[ 1 ] = tmp[ 1 ];
			ip.getW()[ 2 ] = tmp[ 2 ];
		}
	}

	public static int downsampleFactor( final int downsampleXY, final int downsampleZ, final VoxelDimensions v )
	{
		final double calXY = Math.min( v.dimension( 0 ), v.dimension( 1 ) );
		final double calZ = v.dimension( 2 ) * downsampleZ;
		final double log2ratio = Math.log( calZ / calXY ) / Math.log( 2 );

		final double exp2;

		if ( downsampleXY == 0 )
			exp2 = Math.pow( 2, Math.floor( log2ratio ) );
		else
			exp2 = Math.pow( 2, Math.ceil( log2ratio ) );

		return (int)Math.round( exp2 );
	}

	public static RandomAccessibleInterval< FloatType > openAtLowestLevelFloat(
			final ImgLoader imgLoader,
			final ViewId view )
	{
		return openAtLowestLevelFloat( imgLoader, view, null );
	}

	public static RandomAccessibleInterval< FloatType > openAtLowestLevelFloat(
			final ImgLoader imgLoader,
			final ViewId view,
			final AffineTransform3D t )
	{
		final RandomAccessibleInterval< FloatType > input;

		if ( MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			final MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapResolutions();
			final int bestLevel = findLowestResolutionLevel( mrImgLoader, view );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading level " + Util.printCoordinates( mipmapResolutions[ bestLevel ] ) );

			input = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getFloatImage( view.getTimePointId(), bestLevel, false );
			if ( t != null )
				t.set( mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );
		}
		else
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading full-resolution images :( " );

			input = imgLoader.getSetupImgLoader( view.getViewSetupId() ).getFloatImage( view.getTimePointId(), false );
			if ( t != null )
				t.identity();
		}

		return input;
	}

	public static RandomAccessibleInterval openAtLowestLevel(
			final ImgLoader imgLoader,
			final ViewId view )
	{
		return openAtLowestLevel( imgLoader, view, null );
	}

	public static RandomAccessibleInterval openAtLowestLevel(
			final ImgLoader imgLoader,
			final ViewId view,
			final AffineTransform3D t )
	{
		final RandomAccessibleInterval input;

		if ( MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			final MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapResolutions();
			final int bestLevel = findLowestResolutionLevel( mrImgLoader, view );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading level " + Util.printCoordinates( mipmapResolutions[ bestLevel ] ) );

			input = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getImage( view.getTimePointId(), bestLevel );
			if ( t != null )
				t.set( mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );
		}
		else
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading full-resolution images :( " );

			input = imgLoader.getSetupImgLoader( view.getViewSetupId() ).getImage( view.getTimePointId() );
			if ( t != null )
				t.identity();
		}

		return input;
	}

	public static int findLowestResolutionLevel( final MultiResolutionImgLoader mrImgLoader, final ViewId view )
	{
		final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( view.getViewSetupId() ).getMipmapResolutions();

		int maxMul = Integer.MIN_VALUE;
		int bestLevel = -1;

		for ( int i = 0; i < mipmapResolutions.length; ++i )
		{
			int mul = 1;

			for ( int d = 0; d < mipmapResolutions[ i ].length; ++d )
				mul *= mipmapResolutions[ i ][ d ];

			if ( mul > maxMul )
			{
				maxMul = mul;
				bestLevel = i;
			}
		}

		return bestLevel;
	}

	/**
	 * 
	 * @param imgLoader the imgloader
	 * @param vd the view description
	 * @param mipMapTransform - will be filled if downsampling is performed, otherwise identity transform
	 * @param downsampleXY - specify which downsampling (e.g. 1,2,4,8 )
	 * @param downsampleZ - specify which downsampling (e.g. 1,2,4,8 )
	 * @param openCompletely - whether to try to open the file entirely
	 * @return opened image
	 */
	public static RandomAccessibleInterval< FloatType > openAndDownsample(
			final ImgLoader imgLoader,
			final ViewDescription vd,
			final AffineTransform3D mipMapTransform,
			final int downsampleXY,
			final int downsampleZ,
			final boolean openCompletely,
			final ExecutorService taskExecutor )
	{
		IOFunctions.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Requesting Img from ImgLoader (tp=" + vd.getTimePointId() + ", setup=" + vd.getViewSetupId() + ")" );

		if ( downsampleXY > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in XY " + downsampleXY + "x ..." );

		if ( downsampleZ > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in Z " + downsampleZ + "x ..." );

		int dsx = downsampleXY;
		int dsy = downsampleXY;
		int dsz = downsampleZ;

		RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input = null;

		if ( ( dsx > 1 || dsy > 1 || dsz > 1 ) && MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;

			double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapResolutions();

			int bestLevel = 0;
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				double[] factors = mipmapResolutions[ level ];
				
				// this fails if factors are not ints
				final int fx = (int)Math.round( factors[ 0 ] );
				final int fy = (int)Math.round( factors[ 1 ] );
				final int fz = (int)Math.round( factors[ 2 ] );
				
				if ( fx <= dsx && fy <= dsy && fz <= dsz && contains( fx, ds ) && contains( fy, ds ) && contains( fz, ds ) )
					bestLevel = level;
			}

			final int fx = (int)Math.round( mipmapResolutions[ bestLevel ][ 0 ] );
			final int fy = (int)Math.round( mipmapResolutions[ bestLevel ][ 1 ] );
			final int fz = (int)Math.round( mipmapResolutions[ bestLevel ][ 2 ] );

			mipMapTransform.set( mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );

			dsx /= fx;
			dsy /= fy;
			dsz /= fz;

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): " +
					"Using precomputed Multiresolution Images [" + fx + "x" + fy + "x" + fz + "], " +
					"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

			if ( openCompletely )
				input = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), bestLevel, false, LOAD_COMPLETELY );
			else
				input = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), bestLevel, false );
		}
		else
		{
			if ( openCompletely )
				input = imgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), false, LOAD_COMPLETELY );
			else
				input = imgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), false );

			mipMapTransform.identity();
		}

		final ImgFactory< net.imglib2.type.numeric.real.FloatType > f = ((Img<net.imglib2.type.numeric.real.FloatType>)input).factory();

		// the additional downsampling (performed below)
		final AffineTransform3D additonalDS = new AffineTransform3D();
		additonalDS.set( dsx, 0.0, 0.0, 0.0, 0.0, dsy, 0.0, 0.0, 0.0, 0.0, dsz, 0.0 );

		// we need to concatenate since when correcting for the downsampling we first multiply by whatever
		// the manual downsampling did, and just then by the scaling+offset of the HDF5
		//
		// Here is an example of what happens (note that the 0.5 pixel shift is not changed)
		// HDF5 MipMap Transform   (2.0, 0.0, 0.0, 0.5, 0.0, 2.0, 0.0, 0.5, 0.0, 0.0, 2.0, 0.5)
		// Additional Downsampling (4.0, 0.0, 0.0, 0.0, 0.0, 4.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0)
		// Resulting model         (8.0, 0.0, 0.0, 0.5, 0.0, 8.0, 0.0, 0.5, 0.0, 0.0, 4.0, 0.5)
		mipMapTransform.concatenate( additonalDS );

		for ( ;dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ true, false, false }, taskExecutor );

		for ( ;dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, true, false }, taskExecutor );

		for ( ;dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, false, true }, taskExecutor );

		return input;
	}

	private static final boolean contains( final int i, final int[] values )
	{
		for ( final int j : values )
			if ( i == j )
				return true;

		return false;
	}
}
