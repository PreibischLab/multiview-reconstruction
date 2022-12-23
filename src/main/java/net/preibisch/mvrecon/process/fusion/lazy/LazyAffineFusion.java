/*
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2022 Multiview Reconstruction developers.
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

package net.preibisch.mvrecon.process.fusion.lazy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import util.Lazy;

/**
 * BigStitcher Affine Fusion in blocks
 *
 * @author Stephan Preibisch
 * @param <T> type of input and output
 */
public class LazyAffineFusion<T extends RealType<T> & NativeType<T>> implements Consumer<RandomAccessibleInterval<T>>
{
	final T type;
	final long[] globalMin;

	final Converter<FloatType, T> converter;
	final BasicImgLoader imgloader;
	final Collection< ? extends ViewId > viewIds;
	final Map< ViewId, ? extends AffineTransform3D > viewRegistrations;
	final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions;
	final Map< ViewId, ? extends Dimensions > viewDimensions;

	/**
	 * 
	 * @param globalMin - the output RAI typically sits at 0,0...0 because it usually is a CachedCellImage (but the actual interval to process in many blocks sits somewhere else)
	 * @param type - which type to fuse
	 */
	public LazyAffineFusion(
			final Converter<FloatType, T> converter,
			final BasicImgLoader imgloader,
			final Collection< ? extends ViewId > viewIds,
			final Map< ViewId, ? extends AffineTransform3D > viewRegistrations,
			final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions,
			final Map< ViewId, ? extends Dimensions > viewDimensions,
			final long[] globalMin,
			final T type )
	{
		this.globalMin = globalMin;
		this.type = type;

		this.converter = converter;
		this.imgloader = imgloader;
		this.viewIds = viewIds;
		this.viewRegistrations = viewRegistrations;
		this.viewDescriptions = viewDescriptions;
		this.viewDimensions = viewDimensions;
	}

	// Note: the output RAI typically sits at 0,0...0 because it usually is a CachedCellImage
	// (but the actual interval to process in many blocks sits somewhere else) 
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void accept( final RandomAccessibleInterval<T> output )
	{
		// in world coordinates
		final Interval targetBlock = Intervals.translate( new FinalInterval( output ), globalMin );

		// which views to process
		final ArrayList< ViewId > viewIdsToProcess =
				overlappingViewIds( targetBlock, viewIds, viewRegistrations, viewDimensions );

		// nothing to save...
		if ( viewIdsToProcess.size() == 0 )
			return;

		final Pair<RandomAccessibleInterval<FloatType>, AffineTransform3D> fused =
				FusionTools.fuseVirtual(
						imgloader,
						viewRegistrations,
						viewDescriptions,
						viewIdsToProcess,
						true, // use blending
						false, // use content-based
						1, // linear interpolation
						targetBlock,
						Double.NaN, // downsampling
						null ); // intensity adjustments

		final RandomAccessibleInterval<T> converted;

		if ( converter == null && type.getClass().isInstance( new FloatType() ) )
			converted = (RandomAccessibleInterval)fused.getA();
		else
			converted = Converters.convert( fused.getA(), converter, type );

		final Cursor<T> cIn = Views.flatIterable( converted ).cursor();
		final Cursor<T> cOut = Views.flatIterable( output ).cursor();

		final long size = Views.flatIterable( output ).size();

		for ( long i = 0; i < size; ++i )
			cOut.next().set( cIn.next() );
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

	public static final <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> init(
			final Interval fusionInterval,
			final T type,
			final int[] blockSize,
			final Converter<FloatType, T> converter,
			final BasicImgLoader imgloader,
			final Collection< ? extends ViewId > viewIds,
			final Map< ViewId, ? extends AffineTransform3D > viewRegistrations,
			final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions,
			final Map< ViewId, ? extends Dimensions > viewDimensions )
	{
		final long[] min = fusionInterval.minAsLongArray();

		final LazyAffineFusion< T > lazyAffineFusion =
				new LazyAffineFusion<>(
						converter,
						imgloader,
						viewIds,
						viewRegistrations,
						viewDescriptions,
						viewDimensions,
						min,
						type.createVariable() );

		final RandomAccessibleInterval<T> fused =
				Views.translate(
						Lazy.process(
								fusionInterval,
								blockSize,
								type.createVariable(),
								AccessFlags.setOf(),
								lazyAffineFusion ),
						min );

		return fused;
	}

	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		final SpimData2 data = new XmlIoSpimData2( "" ).load( "/Users/preibischs/Documents/Microscopy/Stitching/Truman/standard/dataset.xml");

		final ArrayList< ViewId > viewIds = new ArrayList<>();

		for ( final ViewDescription vd : data.getSequenceDescription().getViewDescriptions().values() )
			if ( data.getSequenceDescription().getMissingViews() != null && !data.getSequenceDescription().getMissingViews().getMissingViews().contains( vd ) )
				if ( vd.getViewSetup().getChannel().getId() == 0 )
					viewIds.add( vd );

		final HashMap< ViewId, Dimensions > viewDimensions = new HashMap<>();

		for ( final ViewId viewId : viewIds )
			viewDimensions.put( viewId, data.getSequenceDescription().getViewDescription( viewId ).getViewSetup().getSize() );

		final HashMap< ViewId, AffineTransform3D > viewRegistrations = new HashMap<>();

		for ( final ViewId viewId : viewIds )
		{
			// TODO: preserve anisotropy
			final ViewRegistration reg = data.getViewRegistrations().getViewRegistration( viewId );

			reg.updateModel();
			viewRegistrations.put( viewId, reg.getModel() );
		}

		final Interval fusionInterval =
				BoundingBoxTools.maximalBoundingBox(
						data,
						viewIds,
						"All Views" );
		// TODO: preserve anisotropy

		final RandomAccessibleInterval<FloatType> fused = LazyAffineFusion.init(
				fusionInterval,
				new FloatType(),
				new int[] { 512, 512, 1 }, // good blocksize for displaying
				null,//(i,o) -> o.set(i),
				data.getSequenceDescription().getImgLoader(),
				viewIds,
				viewRegistrations,
				data.getSequenceDescription().getViewDescriptions(),
				viewDimensions );

		ImageJFunctions.show( fused );
	}
}
