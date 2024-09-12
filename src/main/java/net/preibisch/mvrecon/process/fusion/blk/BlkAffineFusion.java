package net.preibisch.mvrecon.process.fusion.blk;

import static net.imglib2.algorithm.blocks.transform.Transform.Interpolation.NEARESTNEIGHBOR;
import static net.imglib2.algorithm.blocks.transform.Transform.Interpolation.NLINEAR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.models.AffineModel1D;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.algorithm.blocks.BlockAlgoUtils;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.algorithm.blocks.ClampType;
import net.imglib2.algorithm.blocks.convert.Convert;
import net.imglib2.algorithm.blocks.transform.Transform;
import net.imglib2.algorithm.blocks.transform.Transform.Interpolation;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI;
import net.preibisch.mvrecon.process.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.lazy.LazyAffineFusion;
import net.preibisch.mvrecon.process.fusion.lazy.LazyFusionTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class BlkAffineFusion
{
	public static < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval< T > init(
			final Converter< FloatType, T > converter,
			final BasicImgLoader imgloader,
			final Collection< ? extends ViewId > viewIds,
			final Map< ViewId, ? extends AffineTransform3D > viewRegistrations,
			final Map< ViewId, ? extends BasicViewDescription< ? > > viewDescriptions,
			final FusionGUI.FusionType fusionType,
			final int interpolationMethod,
			final Map< ViewId, AffineModel1D > intensityAdjustments,
			final Interval fusionInterval,
			final T type,
			final int[] blockSize )
	{
		// go through the views and check if they are all 2-dimensional
		final boolean is2d = viewIds.stream()
				.map( viewDescriptions::get )
				.map( BasicViewDescription::getViewSetup )
				.filter( BasicViewSetup::hasSize )
				.allMatch( vs -> vs.getSize().dimension( 2 ) == 1 );


		System.out.println( "BlkAffineFusion.init" );

		if ( !supports( is2d, fusionType, intensityAdjustments ) )
		{
			IOFunctions.println( "BlkAffineFusion: Fusion method not supported (yet). Falling back to LazyAffineFusion." );
			return LazyAffineFusion.init( converter, imgloader, viewIds, viewRegistrations, viewDescriptions, fusionType, interpolationMethod, intensityAdjustments, fusionInterval, type, blockSize );
		}

		System.out.println( "  --> let's go!" );

		final HashMap< ViewId, Dimensions > viewDimensions = LazyFusionTools.assembleDimensions( viewIds, viewDescriptions );
		final Interpolation interpolation = ( interpolationMethod == 1 ) ? NLINEAR : NEARESTNEIGHBOR;

		// final Interval bb = boundingBox;
		// boundingBox is targetBlock from accept()
		// maybe we don't need that.
		// LazyAffineFusion also has long[] globalMin
		// sounds like global bounding box
		// we need that, where does it come from
		// ==>
		long[] globalMin = fusionInterval.minAsLongArray();


		// to be able to use the "lowest ViewId" wins strategy
		final List< ? extends ViewId > sortedViewIds = new ArrayList<>( viewIds );
		Collections.sort( sortedViewIds );

		// Which views to process (use un-altered bounding box and registrations).
		// Final filtering happens per Cell.
		// Here we just pre-filter everything outside the fusionInterval.
		final Overlap overlap = new Overlap(
				sortedViewIds,
				viewRegistrations,
				viewDimensions,
				LazyFusionTools.defaultAffineExpansion,
				is2d ? 2 : 3 )
				.filter( fusionInterval );

		final List< BlockSupplier< FloatType > > images = new ArrayList<>( overlap.numViews() );
		final List< BlockSupplier< FloatType > > weights = new ArrayList<>( overlap.numViews() );

		for ( final ViewId viewId : overlap.getViewIds() )
		{
			final AffineTransform3D model = viewRegistrations.get( viewId ).copy();

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			final double[] usedDownsampleFactors = new double[ 3 ];
			RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, model, usedDownsampleFactors );

			final AffineTransform3D transform = concatenateBoundingBoxOffset( model, fusionInterval );

			final BlockSupplier< FloatType > viewBlocks = transformedBlocks(
					Cast.unchecked( inputImg ),
					transform, interpolation );
			images.add( viewBlocks );

			// instantiate blending if necessary
			final float[] blending = Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
			final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

			// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
			FusionTools.adjustBlending( viewDimensions.get( viewId ), Group.pvid( viewId ), blending, border, model );

			final BlockSupplier< FloatType > blend = new BlendingBlockSupplier( inputImg, border, blending, transform );
			weights.add( blend );
		}

		final BlockSupplier< T > blocks = convertToOutputType(
				WeightedAverage.of( images, weights ),
				converter, type );
		return BlockAlgoUtils.cellImg( blocks, fusionInterval.dimensionsAsLongArray(), blockSize );
	}

	private static < T extends NativeType< T > > RandomAccessibleInterval< T > getFusedRandomAccessibleInterval(
			final Interval boundingBox,
			final List< BlockSupplier< FloatType > > images,
			final List< BlockSupplier< FloatType > > blendings,
			final Converter< FloatType, T > converter,
			final T type )
	{
		final BlockSupplier< FloatType > floatBlocks = WeightedAverage.of( images, blendings );
		final BlockSupplier< T > blocks = convertToOutputType( floatBlocks, converter, type );
		return BlockAlgoUtils.cellImg( blocks, boundingBox.dimensionsAsLongArray(), new int[] { 64 } );
	}

	private static < T extends NativeType< T > > BlockSupplier< T > convertToOutputType(
			final BlockSupplier< FloatType > floatBlocks,
			final Converter< FloatType, T > converter,
			final T type )
	{
		if ( converter == null )
		{
			return floatBlocks.andThen( Convert.convert( type, ClampType.CLAMP ) );
		}
		else if ( converter instanceof net.imglib2.display.LinearRange )
		{
			final net.imglib2.display.LinearRange range = ( net.imglib2.display.LinearRange ) converter;
			return floatBlocks
					.andThen( LinearRange.linearRange( range.getMin(), range.getMax() ) )
					.andThen( Convert.convert( type, ClampType.CLAMP ) );
		}
		else
		{
			return floatBlocks.andThen( Convert.convert( type, () -> converter ) );
		}
	}


	private static < T extends NativeType< T > > BlockSupplier< FloatType > transformedBlocks(
			final RandomAccessibleInterval< T > inputImg,
			final AffineTransform3D transform,
			final Interpolation interpolation )
	{
		return BlockSupplier.of( Views.extendBorder( ( inputImg ) ) )
				.andThen( Convert.convert( new FloatType() ) )
				.andThen( Transform.affine( transform, interpolation ) );
	}


	private static AffineTransform3D concatenateBoundingBoxOffset(
			final AffineTransform3D transformFromSource,
			final Interval boundingBoxInTarget )
	{
		final AffineTransform3D t = new AffineTransform3D();
		t.setTranslation(
				-boundingBoxInTarget.min( 0 ),
				-boundingBoxInTarget.min( 1 ),
				-boundingBoxInTarget.min( 2 ) );
		t.concatenate( transformFromSource );
		return t;
	}

	/**
	 * Compute and cache the expanded bounding boxes of all transformed views for per-block overlap determination.
	 * Sort order of {@code viewIds} is maintained for per-block queries. (for FIRST-WINS strategy)
	 */
	private static class Overlap
	{
		private final List< ? extends ViewId > viewIds;

		private final long[] bb;

		private final int numDimensions;

		private final int numViews;

		Overlap(
				final List< ? extends ViewId > viewIds,
				final Map< ? extends ViewId, ? extends AffineTransform3D > viewRegistrations,
				final Map< ? extends ViewId, ? extends Dimensions > viewDimensions,
				final int expandOverlap,
				final int numDimensions )
		{
			this.viewIds = viewIds;
			this.numDimensions = numDimensions;
			final List< Interval > bounds = new ArrayList<>();
			for ( final ViewId viewId : viewIds )
			{
				// expand to be conservative ...
				final AffineTransform3D t = viewRegistrations.get( viewId );
				final Dimensions dim = viewDimensions.get( viewId );
				final RealInterval ri = t.estimateBounds( new FinalInterval( dim ) );
				final Interval boundingBoxLocal = Intervals.largestContainedInterval( ri );
				bounds.add( Intervals.expand( boundingBoxLocal, expandOverlap ) );
			}

			numViews = bounds.size();
			bb = new long[ numViews * numDimensions * 2 ];
		}

		private void setBounds( int i, Interval interval )
		{
			final int o_min = numDimensions * ( 2 * i );
			final int o_max = numDimensions * ( 2 * i + 1 );
			for ( int d = 0; d < numDimensions; d++ )
			{
				bb[ o_min + d ] = interval.min( d );
				bb[ o_max + d ] = interval.max( d );
			}
		}

		private long boundsMin( int i, int d )
		{
			final int o_min = numDimensions * ( 2 * i );
			return bb[ o_min + d ];
		}

		private long boundsMax( int i, int d )
		{
			final int o_max = numDimensions * ( 2 * i + 1 );
			return bb[ o_max + d ];
		}

		int[] getOverlappingViewIndices( final Interval interval )
		{
			final int[] indices = new int[ numViews ];
			final long[] min = interval.minAsLongArray();
			final long[] max = interval.maxAsLongArray();
			int j = 0;
			for ( int i = 0; i < numViews; ++i )
				if ( isOverlapping( i, min, max ) )
					indices[ j++ ] = i;
			return Arrays.copyOf( indices, j );
		}

		List< ViewId > getOverlappingViewIds( final Interval interval )
		{
			final int[] indices = getOverlappingViewIndices( interval );
			final List< ViewId > views = new ArrayList<>();
			for ( final int i : indices )
				views.add( viewIds.get( i ) );
			return views;
		}

		private boolean isOverlapping( final int i, final long[] min, final long[] max )
		{
			for ( int d = 0; d < numDimensions; ++d )
				if ( min[ d ] > boundsMax( i, d ) || max[ d ] < boundsMin( i, d ) )
					return false;
			return true;
		}

		/**
		 * The ViewIds that are checked.
		 * <p>
		 * Elements of {@code int[]} array returned by {@link #getOverlappingViewIds(Interval)}
		 * correspond to indices into this list.
		 */
		public List< ? extends ViewId > getViewIds()
		{
			return viewIds;
		}

		public int numViews()
		{
			return numViews;
		}

		private Overlap(
				final List< ? extends ViewId > viewIds,
				final long[] bb,
				final int numDimensions )
		{
			this.viewIds = viewIds;
			this.bb = bb;
			this.numDimensions = numDimensions;
			numViews = viewIds.size();
		}

		/**
		 * Return a new {@code Overlap}, containing only those ViewIds that overlap the given {@code boundingBox}.
		 *
		 * @param boundingBox
		 *
		 * @return
		 */
		Overlap filter( final Interval boundingBox )
		{
			final int[] indices = getOverlappingViewIndices( boundingBox );
			final int filteredNumViews = indices.length;
			final List< ViewId > filteredViews = new ArrayList<>( filteredNumViews );
			final long[] filteredBB = new long[ filteredNumViews * numDimensions * 2 ];
			for ( final int i : indices )
			{
				final int o = numDimensions * ( 2 * i );
				final int fo = numDimensions * ( 2 * filteredViews.size() );
				for ( int k = 0; k < 2 * numDimensions; ++k )
					filteredBB[ fo + k ] = bb[ o + k ];
				filteredViews.add( viewIds.get( i ) );
			}
			return new Overlap( filteredViews, filteredBB, numDimensions );
		}
	}


	private static < T extends RealType< T > & NativeType< T > > boolean supports(
			final boolean is2d,
			final FusionGUI.FusionType fusionType,
			final Map< ViewId, AffineModel1D > intensityAdjustments )
	{
		if ( is2d )
			return false; // TODO

		switch ( fusionType )
		{
		case AVG_BLEND:
			break;
		case AVG:
		case AVG_CONTENT:
		case AVG_BLEND_CONTENT:
		case MAX:
		case FIRST:
			return false; // TODO
		}

		if ( intensityAdjustments != null )
			return false; // TODO

		return true;
	}
}
