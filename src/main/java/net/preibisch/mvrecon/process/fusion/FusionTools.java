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
package net.preibisch.mvrecon.process.fusion;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import bdv.util.ConstantRandomAccessible;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.models.AffineModel1D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessible;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.ViewSetupUtils;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.DisplayFusedImagesPopup;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.intensityadjust.IntensityAdjuster;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformView;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.TransformWeight;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval.CombineType;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class FusionTools
{
	public static enum ImgDataType { VIRTUAL, CACHED, PRECOMPUTED };
	public static String[] imgDataTypeChoice = new String[]{ "Virtual", "Cached", "Precompute Image" };

	public static float defaultBlendingRange = 40;
	public static float defaultBlendingBorder = 0;

	public static double defaultContentBasedSigma1 = 20;
	public static double defaultContentBasedSigma2 = 40;

	public static long numPixels( final Interval bb, final double downsampling )
	{
		final long[] min = new long[ bb.numDimensions() ];
		final long[] max = new long[ bb.numDimensions() ];

		bb.min( min );
		bb.max( max );

		return numPixels( min, max, downsampling );
	}

	public static long numPixels( final long[] dim, final double downsampling )
	{
		final long[] min = new long[ dim.length ];
		final long[] max = new long[ dim.length ];

		for ( int d = 0; d < dim.length; ++d )
		{
			min[ d ] = 0;
			max[ d ] = dim[ d ] - 1;
		}

		return numPixels( min, max, downsampling );
	}

	public static long numPixels( final long[] min, final long[] max, final double downsampling )
	{
		final double ds;

		if ( Double.isNaN( downsampling ) )
			ds = 1;
		else
			ds = downsampling;

		long numpixels = 1;

		for ( int d = 0; d < min.length; ++d )
			numpixels *= Math.round( (max[ d ] - min[ d ] + 1)/ds );

		return numpixels;
	}

	/**
	 * Virtually fuses views using a maximal bounding box around all views
	 *
	 * @param spimData - an AbstractSpimData object
	 * @param viewIds - which viewIds to fuse (be careful to remove not present one's first)
	 *
	 * @return a virtually fused zeroMin RandomAccessibleInterval and the transformation to map it to global coordinates
	 */
	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? extends ImgLoader > > spimData,
			final Collection< ? extends ViewId > viewIds )
	{
		return fuseVirtual(
				spimData,
				viewIds,
				new BoundingBoxMaximal( viewIds, spimData ).estimate( "Full Bounding Box" ),
				Double.NaN,
				null );
	}

	/**
	 * Virtually fuses views using a maximal bounding box around all views
	 *
	 * @param spimData - an AbstractSpimData object
	 * @param viewIds - which viewIds to fuse (be careful to remove not present one's first)
	 * @param downsampling - desired downsampling, Double.NaN means no downsampling
	 *
	 * @return a virtually fused zeroMin RandomAccessibleInterval and the transformation to map it to global coordinates
	 */
	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? extends ImgLoader > > spimData,
			final Collection< ? extends ViewId > viewIds,
			double downsampling )
	{
		return fuseVirtual(
				spimData,
				viewIds,
				new BoundingBoxMaximal( viewIds, spimData ).estimate( "Full Bounding Box" ),
				downsampling,
				null );
	}

	/**
	 * Virtually fuses views using a maximal bounding box around all views
	 *
	 * @param spimData - an AbstractSpimData object
	 * @param viewIds - which viewIds to fuse (be careful to remove not present one's first)
	 * @param downsampling - desired downsampling, Double.NaN means no downsampling
	 * @param adjustIntensities - adjust intensities according to whats stored in the spimdata
	 *
	 * @return a virtually fused zeroMin RandomAccessibleInterval and the transformation to map it to global coordinates
	 */
	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewIds,
			double downsampling,
			final boolean adjustIntensities )
	{
		return fuseVirtual(
				spimData,
				viewIds,
				new BoundingBoxMaximal( viewIds, spimData ).estimate( "Full Bounding Box" ),
				downsampling,
				adjustIntensities ? spimData.getIntensityAdjustments().getIntensityAdjustments() : null );
	}

	/**
	 * Virtually fuses views
	 *
	 * @param spimData - an AbstractSpimData object
	 * @param viewIds - which viewIds to fuse (be careful to remove not present one's first)
	 * @param bb - the bounding box in world coordinates (can be loaded from XML or defined through one of the BoundingBoxEstimation implementations)
	 * @param downsampling - desired downsampling, Double.NaN means no downsampling
	 *
	 * @return a virtually fused zeroMin RandomAccessibleInterval and the transformation to map it to global coordinates
	 */
	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? > spimData,
			final Collection< ? extends ViewId > viewIds,
			Interval bb,
			double downsampling )
	{
		return fuseVirtual( spimData, viewIds, bb, downsampling, null );
	}

	/**
	 * Virtually fuses views
	 *
	 * @param spimData - an AbstractSpimData object
	 * @param viewIds - which viewIds to fuse (be careful to remove not present one's first)
	 * @param bb - the bounding box in world coordinates (can be loaded from XML or defined through one of the BoundingBoxEstimation implementations)
	 * @param downsampling - desired downsampling, Double.NaN means no downsampling
	 * @param intensityAdjustments - the intensityadjustsments or null
	 *
	 * @return a virtually fused zeroMin RandomAccessibleInterval and the transformation to map it to global coordinates
	 */
	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? > spimData,
			final Collection< ? extends ViewId > viewIds,
			Interval bb,
			double downsampling,
			final Map< ? extends ViewId, AffineModel1D > intensityAdjustments )
	{
		return fuseVirtual( spimData, viewIds, true, false, 1, bb, downsampling, intensityAdjustments );
	}

	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final AbstractSpimData< ? > spimData,
			final Collection< ? extends ViewId > views,
			final boolean useBlending,
			final boolean useContentBased,
			final int interpolation,
			final Interval boundingBox,
			final double downsampling,
			final Map< ? extends ViewId, AffineModel1D > intensityAdjustments )
	{
		final BasicImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();

		final HashMap< ViewId, AffineTransform3D > registrations = new HashMap<>();

		for ( final ViewId viewId : views )
		{
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			registrations.put( viewId, vr.getModel().copy() );
		}

		final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions = spimData.getSequenceDescription().getViewDescriptions();

		return fuseVirtual( imgLoader, registrations, viewDescriptions, views, useBlending, useContentBased, interpolation, boundingBox, downsampling, intensityAdjustments );
	}

	/**
	 * 
	 * @param boundingBox - the bounding box to scale
	 * @param downsampling - the downsampling factor
	 * @return a downsampled interval and the corresponding affine transform to map it to global coordinates or a copy if downsampling == Double.NaN or 1.0
	 */
	public static Pair< Interval, AffineTransform3D > createDownsampledBoundingBox(
			final Interval boundingBox,
			final double downsampling )
	{
		final Interval bbDS;
		
		if ( Double.isNaN( downsampling ) || downsampling == 1.0 )
		{
			bbDS = new FinalInterval( boundingBox );
		}
		else
		{
			bbDS = TransformVirtual.scaleBoundingBox( boundingBox, 1.0 / downsampling );
		}

		// there is rounding when scaling the bounding box ...
		final double[] offset = new double[ boundingBox.numDimensions() ];
		final double[] translation = new double[ boundingBox.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
			translation[ d ] = ( offset[ d ] + bbDS.min( d ) ) * downsampling;

		// the virtual image is zeroMin, this transformation puts it into the global coordinate system
		final AffineTransform3D t = new AffineTransform3D();
		t.scale( downsampling );
		t.translate( translation );

		return new ValuePair<>( bbDS, t );
	}

	public static Interval getFusedZeroMinInterval( final Interval bbDS )
	{
		final long[] dim = new long[ bbDS.numDimensions() ];
		bbDS.dimensions( dim );
		return new FinalInterval( dim );
	}

	public static Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > fuseVirtual(
			final BasicImgLoader imgloader,
			final Map< ViewId, AffineTransform3D > registrations,
			final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions,
			final Collection< ? extends ViewId > views,
			final boolean useBlending,
			final boolean useContentBased,
			final int interpolation,
			final Interval boundingBox,
			final double downsampling,
			final Map< ? extends ViewId, AffineModel1D > intensityAdjustments )
	{

		Interval bBox2d = null;
		// go through the images and check if they are all 2-dimensional
		boolean is_2d = false;
		for ( final ViewId vid: views )
		{
			if ( viewDescriptions.get(vid).getViewSetup().hasSize() )
				if (viewDescriptions.get(vid).getViewSetup().getSize().dimension(2) == 1)
					is_2d = true;
				else
				{
					// TODO: maybe warn that 2d images will be lost during fusion if we have a 2d/3d mixup
					is_2d = false; // we found a non-2d image
					break;
				}
		}

		if (is_2d)
		{
			// set the translational part of the registrations to 0
			for ( AffineTransform3D transform : registrations.values())
			{
				// check if we have just scaling in 3d
				boolean justScale3d = true;
				for (int d1 = 0; d1<3; d1++)
					for (int d2 = 0; d2<3; d2++)
						if ((d1 > 1 || d2>1) && d1 != d2 && transform.get(d1,d2) != 0)
							justScale3d = false;
				if (justScale3d)
					transform.set(0,2,3);
				else
					IOFunctions.println("WARNING: You are trying to fuse 2d images with 3d registrations.");
			}
			// create a virtual 2-d bounding box
			long[] bbMin = new long[3];
			long[] bbMax = new long[3];
			boundingBox.min(bbMin);
			boundingBox.max(bbMax);
			bbMin[2] = bbMax[2] = 0;
			bBox2d = new FinalInterval(bbMin, bbMax);
		}

		final Pair< Interval, AffineTransform3D > scaledBB = createDownsampledBoundingBox( is_2d ? bBox2d : boundingBox, downsampling );
		final Interval bb = scaledBB.getA();
		final AffineTransform3D bbTransform = scaledBB.getB();

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		for ( final ViewId viewId : views )
		{
			AffineTransform3D model = registrations.get( viewId );

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, model );

			if ( intensityAdjustments != null && intensityAdjustments.containsKey( viewId ) )
				inputImg = new ConvertedRandomAccessibleInterval< FloatType, FloatType >(
						convertInput( inputImg ),
						new IntensityAdjuster( intensityAdjustments.get( viewId ) ),
						new FloatType() );

			images.add( TransformView.transformView( inputImg, model, bb, 0, interpolation ) );

			// add all (or no) weighting schemes
			if ( useBlending || useContentBased )
			{
				RandomAccessibleInterval< FloatType > transformedBlending = null, transformedContentBased = null;

				// instantiate blending if necessary
				if ( useBlending )
				{
					final float[] blending = Util.getArrayFromValue( defaultBlendingRange, 3 );
					final float[] border = Util.getArrayFromValue( defaultBlendingBorder, 3 );

					// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
					adjustBlending( viewDescriptions.get( viewId ), blending, border, model );
	
					transformedBlending = TransformWeight.transformBlending( inputImg, border, blending, model, bb );
				}
	
				// instantiate content based if necessary
				if ( useContentBased )
				{
					final double[] sigma1 = Util.getArrayFromValue( defaultContentBasedSigma1, 3 );
					final double[] sigma2 = Util.getArrayFromValue( defaultContentBasedSigma2, 3 );

					// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
					adjustContentBased( viewDescriptions.get( viewId ), sigma1, sigma2, model );

					transformedContentBased = TransformWeight.transformContentBased( inputImg, new CellImgFactory< ComplexFloatType >(), sigma1, sigma2, model, bb );
				}

				if ( useContentBased && useBlending )
				{
					weights.add( new CombineWeightsRandomAccessibleInterval(
									new FinalInterval( transformedBlending ),
									transformedBlending,
									transformedContentBased,
									CombineType.MUL ) );
				}
				else if ( useBlending )
				{
					weights.add( transformedBlending );
				}
				else if ( useContentBased )
				{
					weights.add( transformedContentBased );
				}
			}
			else
			{
				final RandomAccessibleInterval< FloatType > imageArea =
						Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( inputImg ) );

				weights.add( TransformView.transformView( imageArea, model, bb, 0, 0 ) );
			}
		}

		return new ValuePair<>( new FusedRandomAccessibleInterval( new FinalInterval( getFusedZeroMinInterval( bb ) ), images, weights ), bbTransform );
	}

	@SuppressWarnings("unchecked")
	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > convertInput( final RandomAccessibleInterval< T > img )
	{
		if ( FloatType.class.isInstance( Views.iterable( img ).cursor().next() ) )
		{
			return (RandomAccessibleInterval< FloatType >)img;
		}
		else
		{
			return Views.interval( new ConvertedRandomAccessible< T, FloatType >(
						img,
						new RealFloatConverter< T >(),
						new FloatType() ), img );
		}
	}

	public static ImagePlus displayVirtually( final RandomAccessibleInterval< FloatType > input )
	{
		return display( input, ImgDataType.VIRTUAL, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayVirtually( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.VIRTUAL, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCopy( final RandomAccessibleInterval< FloatType > input )
	{
		return display( input, ImgDataType.PRECOMPUTED, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCopy( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.PRECOMPUTED, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCached(
			final RandomAccessibleInterval< FloatType > input,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, ImgDataType.CACHED, cellDim, maxCacheSize );
	}

	public static ImagePlus displayCached(
			final RandomAccessibleInterval< FloatType > input,
			 final double min,
			 final double max,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, ImgDataType.CACHED, min, max, cellDim, maxCacheSize );
	}

	public static ImagePlus displayCached( final RandomAccessibleInterval< FloatType > input )
	{
		return displayCached( input, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCached( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.CACHED, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType )
	{
		return display( input, imgType, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, imgType, 0, 255, cellDim, maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType,
			final double min,
			final double max,
			final int[] cellDim,
			final int maxCacheSize )
	{
		final RandomAccessibleInterval< FloatType > img;

		if ( imgType == ImgDataType.CACHED )
			img = cacheRandomAccessibleInterval( input, maxCacheSize, new FloatType(), cellDim );
		else if ( imgType == ImgDataType.PRECOMPUTED )
			img = copyImg( input, new ImagePlusImgFactory<>(), new FloatType(), null, true );
		else
			img = input;

		// set ImageJ title according to fusion type
		final String title = imgType == ImgDataType.CACHED ? 
				"Fused, Virtual (cached) " : (imgType == ImgDataType.VIRTUAL ? 
						"Fused, Virtual" : "Fused" );

		return DisplayImage.getImagePlusInstance( img, true, title, min, max );
	}

	/**
	 * Compute how much blending in the input has to be done so the target values blending and border are achieved in the fused image
	 *
	 * @param vd - which view
	 * @param blending - the target blending range, e.g. 40
	 * @param border - the target blending border, e.g. 0
	 * @param transformationModel - the transformation model used to map from the (downsampled) input to the output
	 */
	public static void adjustBlending( final BasicViewDescription< ? extends BasicViewSetup > vd, final float[] blending, final float[] border, final AffineTransform3D transformationModel )
	{
		adjustBlending( vd.getViewSetup().getSize(), Group.pvid( vd ), blending, border, transformationModel );
	}

	public static void adjustBlending( final Dimensions dim, final String name, final float[] blending, final float[] border, final AffineTransform3D transformationModel )
	{
		final double[] scale = TransformationTools.scaling( dim, transformationModel ).getA();

		final NumberFormat f = TransformationTools.f;

		System.out.println( "View " + name + " is currently scaled by: (" +
				f.format( scale[ 0 ] ) + ", " + f.format( scale[ 1 ] ) + ", " + f.format( scale[ 2 ] ) + ")" );

		for ( int d = 0; d < blending.length; ++d )
		{
			blending[ d ] /= ( float )scale[ d ];
			border[ d ] /= ( float )scale[ d ];
		}
	}

	/**
	 * Compute how much sigma in the input has to be applied so the target values of sigma1 and 2 are achieved in the fused image
	 *
	 * @param vd - which view
	 * @param sigma1 - the target sigma1 for entropy approximation, e.g. 20
	 * @param sigma2 - the target sigma2 for entropy approximation, e.g. 40
	 * @param transformationModel - the transformation model used to map from the (downsampled) input to the output
	 */
	public static void adjustContentBased( final BasicViewDescription< ? extends BasicViewSetup > vd, final double[] sigma1, final double[] sigma2, final AffineTransform3D transformationModel )
	{
		final double[] scale = TransformationTools.scaling( vd.getViewSetup().getSize(), transformationModel ).getA();

		for ( int d = 0; d < sigma1.length; ++d )
		{
			sigma1[ d ] /= ( float )scale[ d ];
			sigma2[ d ] /= ( float )scale[ d ];
		}
	}

	public static double getMinRes( final BasicViewDescription< ? extends BasicViewSetup > desc )
	{
		final VoxelDimensions size = ViewSetupUtils.getVoxelSize( desc.getViewSetup() );

		if ( size == null )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): WARNINIG, could not load voxel size!! Assuming 1,1,1"  );
			return 1;
		}

		return Math.min( size.dimension( 0 ), Math.min( size.dimension( 1 ), size.dimension( 2 ) ) );
	}

	public static String getIllumName( final List< Illumination > illumsToProcess )
	{
		String illumName = "_Ill" + illumsToProcess.get( 0 ).getName();

		for ( int i = 1; i < illumsToProcess.size(); ++i )
			illumName += "," + illumsToProcess.get( i ).getName();

		return illumName;
	}

	public static String getAngleName( final List< Angle > anglesToProcess )
	{
		String angleName = "_Ang" + anglesToProcess.get( 0 ).getName();

		for ( int i = 1; i < anglesToProcess.size(); ++i )
			angleName += "," + anglesToProcess.get( i ).getName();

		return angleName;
	}

	public static final ArrayList< ViewDescription > assembleInputData(
			final SpimData2 spimData,
			final TimePoint timepoint,
			final Channel channel,
			final List< ViewId > viewIdsToProcess )
	{
		final ArrayList< ViewDescription > inputData = new ArrayList< ViewDescription >();

		for ( final ViewId viewId : viewIdsToProcess )
		{
			final ViewDescription vd = spimData.getSequenceDescription().getViewDescription(
					viewId.getTimePointId(), viewId.getViewSetupId() );

			if ( !vd.isPresent() || vd.getTimePointId() != timepoint.getId() || vd.getViewSetup().getChannel().getId() != channel.getId() )
				continue;

			// get the most recent model
			spimData.getViewRegistrations().getViewRegistration( viewId ).updateModel();

			inputData.add( vd );
		}

		return inputData;
	}

	public static < T extends NativeType< T > > RandomAccessibleInterval< T > cacheRandomAccessibleInterval(
			final RandomAccessibleInterval< T > input,
			final long maxCacheSize,
			final T type,
			final int... cellDim )
	{
		final RandomAccessibleInterval< T > in;

		if ( Views.isZeroMin( input ) )
			in = input;
		else
			in = Views.zeroMin( input );
		
		final ReadOnlyCachedCellImgOptions options = new ReadOnlyCachedCellImgOptions().cellDimensions( cellDim ).maxCacheSize( maxCacheSize );
		final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory( options );

		final CellLoader< T > loader = new CellLoader< T >()
		{
			@Override
			public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
			{
				final Cursor< T > cursor = cell.localizingCursor();
				final RandomAccess< T > ra = in.randomAccess();
				
				while( cursor.hasNext() )
				{
					cursor.fwd();
					ra.setPosition( cursor );
					cursor.get().set( ra.get() );
				}
			}
		};

		final long[] dim = new long[ in.numDimensions() ];
		in.dimensions( dim );

		return translateIfNecessary( input, factory.create( dim, type, loader ) );
	}

	public static < T extends Type< T > > RandomAccessibleInterval< T > copyImg( final RandomAccessibleInterval< T > input, final ImgFactory< T > factory, final T type, final ExecutorService service  )
	{
		return copyImg( input, factory, type, service, false );
	}

	public static < T extends Type< T > > RandomAccessibleInterval< T > copyImg(
			final RandomAccessibleInterval< T > input,
			final ImgFactory< T > factory,
			final T type,
			final ExecutorService service,
			final boolean showProgress  )
	{
		return translateIfNecessary( input, copyImgNoTranslation( input, factory, type, service, showProgress ) );
	}

	public static < T extends Type< T > > Img< T > copyImgNoTranslation( final RandomAccessibleInterval< T > input, final ImgFactory< T > factory, final T type, final ExecutorService service )
	{
		return copyImgNoTranslation( input, factory, type, service, false );
	}

	public static < T extends Type< T > > Img< T > copyImgNoTranslation(
			final RandomAccessibleInterval< T > input,
			final ImgFactory< T > factory,
			final T type,
			final ExecutorService service,
			final boolean showProgress  )
	{
		final RandomAccessibleInterval< T > in;

		if ( Views.isZeroMin( input ) )
			in = input;
		else
			in = Views.zeroMin( input );

		final long[] dim = new long[ in.numDimensions() ];
		in.dimensions( dim );

		final Img< T > tImg = factory.create( dim, type );

		// copy the virtual construct into an actual image
		copyImg( in, tImg, service, showProgress );

		return tImg;
	}

	public static < T > RandomAccessibleInterval< T > translateIfNecessary( final Interval original, final RandomAccessibleInterval< T > copy )
	{
		if ( Views.isZeroMin( original ) )
		{
			return copy;
		}
		else
		{
			final long[] min = new long[ original.numDimensions() ];
			original.min( min );

			return Views.translate( copy, min );
		}
	}

	public static void copyImg( final RandomAccessibleInterval< FloatType > input, final RandomAccessibleInterval< FloatType > output, final ExecutorService service )
	{
		copyImg( input, output, service, false );
	}

	public static < T extends Type< T > > void copyImg( final RandomAccessibleInterval< T > input, final RandomAccessibleInterval< T > output, final ExecutorService service, final boolean showProgress )
	{
		final long numPixels = Views.iterable( input ).size();
		final int nThreads = Threads.numThreads();
		final Vector< ImagePortion > portions = divideIntoPortions( numPixels );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		final AtomicInteger progress = new AtomicInteger( 0 );

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					copyImg( portion.getStartPosition(), portion.getLoopSize(), input, output );

					if ( showProgress )
						IJ.showProgress( (double)progress.incrementAndGet() / tasks.size() );

					return null;
				}
			});
		}

		if ( showProgress )
			IJ.showProgress( 0.01 );

		if ( service == null )
			execTasks( tasks, nThreads, "copy image" );
		else
			execTasks( tasks, service, "copy image" );
	}

	public static final void execTasks( final ArrayList< Callable< Void > > tasks, final int nThreads, final String jobDescription )
	{
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( nThreads );

		execTasks( tasks, Executors.newFixedThreadPool( nThreads ), jobDescription );

		taskExecutor.shutdown();
	}

	public static final void execTasks( final ArrayList< Callable< Void > > tasks, final ExecutorService taskExecutor, final String jobDescription )
	{
		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to " + jobDescription + ": " + e );
			e.printStackTrace();
			return;
		}
	}

	/*
	 * One thread of a method to compute the quotient between two images of the multiview deconvolution
	 * 
	 * @param start
	 * @param loopSize
	 * @param source
	 * @param target
	 */
	public static final < T extends Type< T > > void copyImg(
			final long start,
			final long loopSize,
			final RandomAccessibleInterval< T > source,
			final RandomAccessibleInterval< T > target )
	{
		final IterableInterval< T > sourceIterable = Views.iterable( source );
		final IterableInterval< T > targetIterable = Views.iterable( target );

		if ( sourceIterable.iterationOrder().equals( targetIterable.iterationOrder() ) )
		{
			final Cursor< T > cursorSource = sourceIterable.cursor();
			final Cursor< T > cursorTarget = targetIterable.cursor();
	
			cursorSource.jumpFwd( start );
			cursorTarget.jumpFwd( start );
	
			for ( long l = 0; l < loopSize; ++l )
				cursorTarget.next().set( cursorSource.next() );
		}
		else
		{
			final RandomAccess< T > raSource = source.randomAccess();
			final Cursor< T > cursorTarget = targetIterable.localizingCursor();

			cursorTarget.jumpFwd( start );

			for ( long l = 0; l < loopSize; ++l )
			{
				cursorTarget.fwd();
				raSource.setPosition( cursorTarget );

				cursorTarget.get().set( raSource.get() );
			}
		}
	}

	public static < T extends RealType< T > > float[] minMax( final RandomAccessibleInterval< T > img )
	{
		final IterableInterval< T > iterable = Views.iterable( img );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = divideIntoPortions( iterable.size() );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< float[] > > tasks = new ArrayList< Callable< float[] > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< float[] >() 
					{
						@Override
						public float[] call() throws Exception
						{
							float min = Float.MAX_VALUE;
							float max = -Float.MAX_VALUE;
							
							final Cursor< T > c = iterable.cursor();
							c.jumpFwd( portion.getStartPosition() );
							
							for ( long j = 0; j < portion.getLoopSize(); ++j )
							{
								final float v = c.next().getRealFloat();
								
								min = Math.min( min, v );
								max = Math.max( max, v );
							}
							
							// min & max of this portion
							return new float[]{ min, max };
						}
					});
		}
		
		// run threads and combine results
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		
		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< float[] > > futures = taskExecutor.invokeAll( tasks );
			
			for ( final Future< float[] > future : futures )
			{
				final float[] minmax = future.get();
				min = Math.min( min, minmax[ 0 ] );
				max = Math.max( max, minmax[ 1 ] );
			}
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute min/max: " + e );
			e.printStackTrace();
			return null;
		}

		taskExecutor.shutdown();
		
		return new float[]{ min, max };
	}

	public static < T extends RealType< T > > double[] minMaxApprox( final RandomAccessibleInterval< T > img )
	{
		return minMaxApprox( img, 1000 );
	}
	
	public static < T extends RealType< T > > double[] minMaxApprox( final RandomAccessibleInterval< T > img, final int numPixels )
	{
		return minMaxApprox( img, new Random( 3535 ), numPixels );
	}

	public static < T extends RealType< T > > double[] minMaxApprox( final RandomAccessibleInterval< T > img, final Random rnd, final int numPixels )
	{
		final RandomAccess< T > ra = img.randomAccess();

		// run threads and combine results
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( int i = 0; i < numPixels; ++i )
		{
			for ( int d = 0; d < img.numDimensions(); ++d )
				ra.setPosition( rnd.nextInt( (int)img.dimension( d ) ) + (int)img.min( d ), d );

			final double v = ra.get().getRealDouble();

			min = Math.min( min, v );
			max = Math.max( max, v );
		}

		return new double[]{ min, max };
	}

	public static < T extends RealType< T > > double[] minMaxAvgApprox( final RandomAccessibleInterval< T > img, final Random rnd, final int numPixels )
	{
		final RandomAccess< T > ra = img.randomAccess();

		// run threads and combine results
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		final RealSum realSum = new RealSum( numPixels );

		for ( int i = 0; i < numPixels; ++i )
		{
			for ( int d = 0; d < img.numDimensions(); ++d )
				ra.setPosition( rnd.nextInt( (int)img.dimension( d ) ) + (int)img.min( d ), d );

			final double v = ra.get().getRealDouble();

			min = Math.min( min, v );
			max = Math.max( max, v );
			realSum.add( v );
		}

		return new double[]{ min, max, realSum.getSum() / (double)numPixels };
	}

	/**
	 * Normalizes the image to the range [0...1]
	 * 
	 * @param img - the image to normalize
	 * @return - normalized array
	 */
	public static boolean normalizeImage( final RandomAccessibleInterval< FloatType > img )
	{
		final float minmax[] = minMax( img );
		final float min = minmax[ 0 ];
		final float max = minmax[ 1 ];
		
		return normalizeImage( img, min, max );
	}

	/**
	 * Normalizes the image to the range [0...1]
	 * 
	 * @param img - the image to normalize
	 * @param min - min value
	 * @param max - max value
	 * @return - normalized array
	 */
	public static boolean normalizeImage( final RandomAccessibleInterval< FloatType > img, final float min, final float max )
	{
		final float diff = max - min;

		if ( Float.isNaN( diff ) || Float.isInfinite(diff) || diff == 0 )
		{
			IOFunctions.println( "Cannot normalize image, min=" + min + "  + max=" + max );
			return false;
		}

		final IterableInterval< FloatType > iterable = Views.iterable( img );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = divideIntoPortions( iterable.size() );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< String > > tasks = new ArrayList< Callable< String > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< String >() 
					{
						@Override
						public String call() throws Exception
						{
							final Cursor< FloatType > c = iterable.cursor();
							c.jumpFwd( portion.getStartPosition() );
							
							for ( long j = 0; j < portion.getLoopSize(); ++j )
							{
								final FloatType t = c.next();
								
								final float norm = ( t.get() - min ) / diff;
								t.set( norm );
							}
							
							return "";
						}
					});
		}
		
		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to compute min/max: " + e );
			e.printStackTrace();
			return false;
		}

		taskExecutor.shutdown();
		
		return true;
	}


	public static final Vector<ImagePortion> divideIntoPortions( final long imageSize )
	{
		int numPortions;

		if ( imageSize <= Threads.numThreads() )
			numPortions = (int)imageSize;
		else
			numPortions = Math.max( Threads.numThreads(), (int)( imageSize / ( 64l*64l*64l ) ) );

		//System.out.println( "nPortions for copy:" + numPortions );

		final Vector<ImagePortion> portions = new Vector<ImagePortion>();

		if ( imageSize == 0 )
			return portions;

		long threadChunkSize = imageSize / numPortions;

		while ( threadChunkSize == 0 )
		{
			--numPortions;
			threadChunkSize = imageSize / numPortions;
		}

		long threadChunkMod = imageSize % numPortions;

		for ( int portionID = 0; portionID < numPortions; ++portionID )
		{
			// move to the starting position of the current thread
			final long startPosition = portionID * threadChunkSize;

			// the last thread may has to run longer if the number of pixels cannot be divided by the number of threads
			final long loopSize;
			if ( portionID == numPortions - 1 )
				loopSize = threadChunkSize + threadChunkMod;
			else
				loopSize = threadChunkSize;
			
			portions.add( new ImagePortion( startPosition, loopSize ) );
		}
		
		return portions;
	}

	public static void runThreads( final Thread[] threads )
	{
		for ( int ithread = 0; ithread < threads.length; ++ithread )
			threads[ ithread ].start();

		try
		{
			for ( int ithread = 0; ithread < threads.length; ++ithread )
				threads[ ithread ].join();
		}
		catch ( InterruptedException ie ) { throw new RuntimeException(ie); }
	}
}
