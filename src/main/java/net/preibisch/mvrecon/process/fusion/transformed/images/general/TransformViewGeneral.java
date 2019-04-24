package net.preibisch.mvrecon.process.fusion.transformed.images.general;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;

public class TransformViewGeneral
{
	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final SpimData data,
			final ViewId viewId,
			final FinalInterval boundingBox,
			final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory,
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory )
	{
		final ImgLoader il = data.getSequenceDescription().getImgLoader();
		final ViewRegistration vr = data.getViewRegistrations().getViewRegistration( viewId );
		vr.updateModel();
		
		return transformView( il, viewId, vr.getModel(), boundingBox, outOfBoundsFactory, interpolatorFactory );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final ImgLoader imgloader,
			final ViewId viewId,
			final AffineTransform3D transform,
			final FinalInterval boundingBox,
			final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory,
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory )
	{
		final RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, transform );//imgloader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

		return transformView( inputImg, transform, boundingBox, outOfBoundsFactory, interpolatorFactory );
	}

	/**
	 * Creates a virtual construct that transforms and zero-mins using a user-defined outofbounds &amp; interpolation.
	 * 
	 * @param input - the input image
	 * @param transform - the affine transformation
	 * @param boundingBox - the bounding box (after transformation)
	 * @param outOfBoundsFactory - outofboundsfactory
	 * @param interpolatorFactory - any interpolatorfactory
	 * @param <T> type
	 * @return transformed image
	 */
	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final RandomAccessibleInterval< T > input,
			final AffineTransform3D transform,
			final Interval boundingBox,
			final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory,
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory )
	{
		final long[] offset = new long[ input.numDimensions() ];
		final long[] size = new long[ input.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] = boundingBox.min( d );
			size[ d ] = boundingBox.dimension( d );
		}

		final TransformedInputGeneralRandomAccessible< T > virtual = new TransformedInputGeneralRandomAccessible< T >( input, transform, outOfBoundsFactory, interpolatorFactory, offset );

		return Views.interval( virtual, new FinalInterval( size ) );
	}

}
