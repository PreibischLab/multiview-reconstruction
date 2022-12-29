package net.preibisch.mvrecon.process.fusion.lazy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoGImgLib2;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import util.Lazy;

public class LazyFusionTools {

	public static int[] defaultBlockSize2d = new int[] { 256, 256 };
	public static int[] defaultBlockSize3d = DoGImgLib2.blockSize;

	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> initLazy(
			final Consumer<RandomAccessibleInterval<T>> consumer,
			final Interval fusionInterval,
			final int[] blockSize,
			final T type )
	{
		final RandomAccessibleInterval<T> fused =
				Views.translate(
						Lazy.process(
								fusionInterval,
								blockSize,
								type.createVariable(),
								AccessFlags.setOf(),
								consumer ),
						fusionInterval.minAsLongArray() );

		return fused;
	}

	public static double estimateAnisotropy(
			final SpimData data,
			final List< ViewId > viewIds )
	{
		return TransformationTools.getAverageAnisotropyFactor( data, viewIds );
	}

	public static BoundingBox getBoundingBox(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String boundingBoxName )
			throws IllegalArgumentException
	{
		BoundingBox bb = null;

		if ( boundingBoxName == null )
		{
			bb = BoundingBoxTools.maximalBoundingBox( data, viewIds, "All Views" );
		}
		else
		{
			final List<BoundingBox> boxes = BoundingBoxTools.getAllBoundingBoxes( data, null, false );

			for ( final BoundingBox box : boxes )
				if ( box.getTitle().equals( boundingBoxName ) )
					bb = box;

			if ( bb == null )
			{
				throw new IllegalArgumentException( "Bounding box '" + boundingBoxName + "' not present in XML." );
			}
		}

		return bb;
	}

	public static HashMap< ViewId, Dimensions > assembleDimensions(
			final Collection<? extends ViewId> viewIds,
			final SpimData data )
	{
		return assembleDimensions(viewIds, data.getSequenceDescription().getViewDescriptions() );
	}

	public static HashMap< ViewId, Dimensions > assembleDimensions(
			final Collection< ? extends ViewId > viewIds,
			final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions )
	{
		final HashMap< ViewId, Dimensions > viewDimensions = new HashMap<>();

		for ( final ViewId viewId : viewIds )
			viewDimensions.put( viewId, viewDescriptions.get( viewId ).getViewSetup().getSize() );

		return viewDimensions;
	}

	public static final ArrayList< ViewId > overlappingViewIds(
			final Interval targetBlock,
			final Collection< ? extends ViewId > allViewIds,
			final Map< ? extends ViewId, ? extends AffineTransform3D > viewRegistrations,
			final Map< ? extends ViewId, ? extends Dimensions > viewDimensions )
	{
		final ArrayList< ViewId > overlappingViewIds = new ArrayList<>();

		for ( final ViewId viewId : allViewIds )
		{
			// expand to be conservative ...
			final AffineTransform3D t = viewRegistrations.get( viewId );
			final Dimensions dim = viewDimensions.get( viewId );
			final RealInterval ri = t.estimateBounds( new FinalInterval( dim ) );
			final Interval boundingBoxLocal = Intervals.largestContainedInterval( ri );
			final Interval bounds = Intervals.expand( boundingBoxLocal, 2 );

			if ( overlaps( targetBlock, bounds ) )
				overlappingViewIds.add( viewId );
		}

		return overlappingViewIds;
	}

	public static boolean overlaps( final Interval interval1, final Interval interval2 )
	{
		final Interval intersection = Intervals.intersect( interval1, interval2 );

		for ( int d = 0; d < intersection.numDimensions(); ++d )
			if ( intersection.dimension( d ) < 0 )
				return false;

		return true;
	}

}
