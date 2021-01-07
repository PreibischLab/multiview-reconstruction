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
package net.preibisch.mvrecon.process.downsampling;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import util.ImgLib2Tools;

public class DownsampleTools
{
	protected static final int[] ds = { 1, 2, 4, 8, 16, 32, 64, 128 };

	/**
	 * Opens the image at an appropriate resolution for the provided transformation and concatenates an extra transform 
	 * 
	 * @param imgLoader - the img loader
	 * @param viewId - the view id
	 * @param m - WILL BE MODIFIED IF OPENED DOWNSAMPLED
	 * @return - opened image
	 */
	@SuppressWarnings("rawtypes")
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
	@SuppressWarnings("rawtypes")
	public static RandomAccessibleInterval openDownsampled( final BasicImgLoader imgLoader, final ViewId viewId, final AffineTransform3D m, final double[] usedDownsampleFactors )
	{
		final Pair< RandomAccessibleInterval, AffineTransform3D > opened = openDownsampled2( imgLoader, viewId, m, usedDownsampleFactors );

		// concatenate the downsampling transformation model to the affine transform
		if ( opened.getB() != null )
			m.concatenate( opened.getB() );

		return opened.getA();
	}
	
	/**
	 * Opens the image at an appropriate resolution for the provided transformation and concatenates an extra transform 
	 * 
	 * @param imgLoader - the img loader
	 * @param viewId - the view id
	 * @param m - NOT modified
	 * @param usedDownsampleFactors - which downsample factors were used to open the image (important for weights etc)
	 * @return - opened image and the affine transform that needs to be concatenated
	 */
	@SuppressWarnings("rawtypes")
	public static Pair< RandomAccessibleInterval, AffineTransform3D > openDownsampled2( final BasicImgLoader imgLoader, final ViewId viewId, final AffineTransform3D m, final double[] usedDownsampleFactors )
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

			// now done in the more specific code above
			// concatenate the downsampling transformation model to the affine transform
			// m.concatenate( mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );

			System.out.println( "Choosing resolution level: " + mipmapResolutions[ bestLevel ][ 0 ] + " x " + mipmapResolutions[ bestLevel ][ 1 ] + " x " + mipmapResolutions[ bestLevel ][ 2 ] );

			if ( usedDownsampleFactors != null && usedDownsampleFactors.length == mipmapResolutions[ bestLevel ].length )
				for ( int d = 0; d < usedDownsampleFactors.length; ++d )
					usedDownsampleFactors[ d ] = mipmapResolutions[ bestLevel ][ d ];

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): "
					+ "Requesting Img from ImgLoader (tp=" + viewId.getTimePointId() + ", setup=" + viewId.getViewSetupId() + "), using level=" + bestLevel + ", [" + mipmapResolutions[ bestLevel ][ 0 ] + " x " + mipmapResolutions[ bestLevel ][ 1 ] + " x " + mipmapResolutions[ bestLevel ][ 2 ] + "]" );

			return new ValuePair<>(
					mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId(), bestLevel ),
					mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );
		}
		else
		{
			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): "
					+ "Requesting Img from ImgLoader (tp=" + viewId.getTimePointId() + ", setup=" + viewId.getViewSetupId() + "), using level=" + 0 + ", [1 x 1 x 1]" );

			return new ValuePair<>( imgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() ), null );
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

	@SuppressWarnings("rawtypes")
	public static RandomAccessibleInterval openAtLowestLevel(
			final ImgLoader imgLoader,
			final ViewId view )
	{
		return openAtLowestLevel( imgLoader, view, null );
	}

	@SuppressWarnings("rawtypes")
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
	 * Opens the image at a specified downsampling level (e.g. 4,4,1). It finds the closest available mipmap level and then downsamples
	 * to reach the target level
	 *
	 * @param imgLoader the imgloader
	 * @param vd the view id
	 * @param mipMapTransform - will be filled if downsampling is performed, otherwise identity transform
	 * @param downsampleFactors - specify which downsampling in each dimension (e.g. 1,2,4,8 )
	 * @param transformOnly - if true does not open any images but only provides the mipMapTransform (METHOD WILL RETURN NULL!)
	 * @param openAsFloat - call imgLoader.getFloatImage() instead of imgLoader.getImage()
	 * @param openCompletely - whether to try to open the file entirely (only required by legacy ImgLib1 code!!!)
	 * @param service - the ExecutorService
	 * @return opened image
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static RandomAccessibleInterval openAndDownsample(
			final BasicImgLoader imgLoader,
			final ViewId vd,
			final AffineTransform3D mipMapTransform,
			long[] downsampleFactors,
			final boolean transformOnly,
			final boolean openAsFloat,
			final boolean openCompletely,
			final ExecutorService service ) // only for ImgLib1 legacy code
	{

		if ( !transformOnly )
			IOFunctions.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Requesting Img from ImgLoader (tp=" + vd.getTimePointId() + ", setup=" + vd.getViewSetupId() + "), downsampling: " + Util.printCoordinates( downsampleFactors ) );

		long dsx = downsampleFactors[0];
		long dsy = downsampleFactors[1];
		long dsz = (downsampleFactors.length > 2) ? downsampleFactors[ 2 ] : 1;

		RandomAccessibleInterval input = null;

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

			if ( mipMapTransform != null )
				mipMapTransform.set( mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );

			dsx /= fx;
			dsy /= fy;
			dsz /= fz;

			if ( !transformOnly )
			{
				IOFunctions.println(
						"(" + new Date(System.currentTimeMillis()) + "): " +
						"Using precomputed Multiresolution Images [" + fx + "x" + fy + "x" + fz + "], " +
						"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

				// we only need to do the complete opening when we do not perform additional downsampling below
				if ( openCompletely && (dsx == 1 && dsy == 1 && dsz == 1 ) )
				{
					// TODO: only needed by ImgLib1 legacy code, remove that!
					input = openCompletely( mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ), vd.getTimePointId(), bestLevel, openAsFloat, false );
				}
				else
				{
					if ( openAsFloat )
						input = ImgLib2Tools.convertVirtual( (RandomAccessibleInterval)mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId(), bestLevel ) );
					else
						input = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId(), bestLevel );
				}
			}
		}
		else
		{
			if ( !transformOnly )
			{
				IOFunctions.println(
						"(" + new Date(System.currentTimeMillis()) + "): " +
						"Using precomputed Multiresolution Images [1x1x1], " +
						"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

				// we only need to do the complete opening when we do not perform additional downsampling below
				if ( openCompletely && (dsx == 1 && dsy == 1 && dsz == 1 ) )
				{
					// TODO: only needed by ImgLib1 legacy code, remove that!
					input = openCompletely( imgLoader.getSetupImgLoader( vd.getViewSetupId() ), vd.getTimePointId(), openAsFloat, false );
				}
				else
				{
					if ( openAsFloat )
						input = ImgLib2Tools.convertVirtual( (RandomAccessibleInterval)imgLoader.getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId() ) );
					else
						input = imgLoader.getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId() );
				}
			}

			if ( mipMapTransform != null )
				mipMapTransform.identity();
		}

		if ( mipMapTransform != null )
		{
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
		}

		if ( !transformOnly )
		{
			ImgFactory  f = null;

			if ( Img.class.isInstance( input ))
			{
				// factory is not implemented for e.g. LazyCellImg yet
				try
				{
					f = ((Img)input).factory();
				}
				catch (UnsupportedOperationException e) {}
			}

			if ( f == null )
			{
				final NativeType< ? > t = Util.getTypeFromInterval( input );
				f = new CellImgFactory( t );
			}

			// note: every pixel is read exactly once, therefore caching the virtual input would not give any advantages
			for ( ;dsx > 1; dsx /= 2 )
				input = Downsample.simple2x( input, f, new boolean[]{ true, false, false }, service );

			for ( ;dsy > 1; dsy /= 2 )
				input = Downsample.simple2x( input, f, new boolean[]{ false, true, false }, service );

			for ( ;dsz > 1; dsz /= 2 )
				input = Downsample.simple2x( input, f, new boolean[]{ false, false, true }, service );
		}

		return input;
	}

	// TODO: REMOVE IMGLIB1 stuff!!
	// required by legacy code that wraps to imglib1
	public static Img<FloatType> openCompletely( final MultiResolutionSetupImgLoader< ? > loader, final int timepointId, final int level, final boolean openAsFloat, final boolean normalize )
	{
		return openCompletely( loader.getImage( timepointId, level ), openAsFloat, normalize );
	}

	// TODO: REMOVE IMGLIB1 stuff!!
	// required by legacy code that wraps to imglib1
	public static Img<FloatType> openCompletely( final BasicSetupImgLoader< ? > loader, final int timepointId, final boolean openAsFloat, final boolean normalize )
	{
		return openCompletely( loader.getImage( timepointId ), openAsFloat, normalize );
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected static Img<FloatType> openCompletely( final RandomAccessibleInterval img, final boolean openAsFloat, final boolean normalize )
	{
		if ( openAsFloat )
		{
			final Img< FloatType > floatImg = new CellImgFactory<FloatType>( new FloatType() ).create( img );

			// TODO: replace with multithreaded RealTypeConverters.copyFromTo( ushortImg, floatImg );
			copyFromToMultithreaded( img, floatImg );

			if ( normalize )
				// normalize the image to 0...1
				normalize( floatImg );

			return floatImg;
		}
		else
		{
			final NativeType< ? > t = Util.getTypeFromInterval( img );
			final Img completeImg = new CellImgFactory( t ).create( img );

			// TODO: replace with multithreaded RealTypeConverters.copyFromTo( ushortImg, floatImg );
			copyFromToMultithreaded( img, completeImg );

			return completeImg;
		}
	}

	// TODO: Remove when RealTypeConvertes.copyFromTo has multithreading support
	public static void copyFromToMultithreaded(
			final RandomAccessible< ? extends RealType< ? > > source,
			final RandomAccessibleInterval< ? extends RealType< ? > > destination )
	{
		final IntervalView< ? extends RealType< ? > > sourceInterval = Views.interval( source, destination );
		final RealType< ? > s = net.imglib2.util.Util.getTypeFromInterval( sourceInterval );
		final RealType< ? > d = net.imglib2.util.Util.getTypeFromInterval( destination );
		final Converter< RealType< ? >, RealType< ? > > copy = RealTypeConverters.getConverter( s, d );
		LoopBuilder.setImages( sourceInterval, destination ).multiThreaded().forEachPixel( copy::convert );
	}

	private static float[] getMinMax( final IterableInterval< FloatType > img )
	{
		float currentMax = img.firstElement().get();
		float currentMin = currentMax;
		for ( final FloatType t : img )
		{
			final float f = t.get();
			if ( f > currentMax )
				currentMax = f;
			else if ( f < currentMin )
				currentMin = f;
		}

		return new float[] { currentMin, currentMax };
	}

	/**
	 * normalize img to 0...1 in place
	 */
	public static void normalize( final IterableInterval< FloatType > img )
	{
		final float[] minmax = getMinMax( img );
		final float min = minmax[ 0 ];
		final float max = minmax[ 1 ];
		final float scale = ( float ) ( 1.0 / ( max - min ) );
		for ( final FloatType t : img )
			t.set( ( t.get() - min ) * scale );
	}

	private static final boolean contains( final int i, final int[] values )
	{
		for ( final int j : values )
			if ( i == j )
				return true;

		return false;
	}
}
