package net.preibisch.mvrecon.process.fusion.blk;

import static net.imglib2.util.Util.safeInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.algorithm.blocks.UnaryBlockOperator;
import net.imglib2.algorithm.blocks.convert.Convert;
import net.imglib2.blocks.BlockInterval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.interval.IntervalSamplingMethod;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleIntervalCursor;
import net.imglib2.view.Views;
import net.imglib2.view.fluent.RealRandomAccessibleView;
import net.imglib2.view.fluent.RandomAccessibleIntervalView.Extension;
import net.imglib2.view.fluent.RandomAccessibleView.Interpolation;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI.FusionType;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.process.fusion.intensity.Coefficients;
import net.preibisch.mvrecon.process.fusion.intensity.FastLinearIntensityMap;
import net.preibisch.mvrecon.process.fusion.lazy.LazyFusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.splitting.SplittingTools;
import util.BlockSupplierUtils;

public class BlkThinPlateSplineFusion
{
	public static int defaultExpansion = LazyFusionTools.defaultNonrigidExpansion;

	public static < T extends RealType< T > & NativeType< T > > BlockSupplier< T > init(
			final Converter< FloatType, T > converter,
			final SplitViewerImgLoader splitImgLoader,
			final Collection< ? extends ViewId > splitViewIdsInput,
			final Map< ViewId, ViewRegistration > splitViewRegistrations, // already adjusted for anisotropy
			final Map< ViewId, ? extends BasicViewDescription< ? > > splitViewDescriptions,
			final FusionType fusionType,
			final double anisotropyFactor,
			final Map< Integer, Integer > fusionMap, // old setupId > new setupId for fusion order, only makes sense with FusionType.FIRST_LOW or FusionType.FIRST_HIGH
			final int interpolationMethod,
			final Map< ViewId, Coefficients > intensityAdjustmentCoefficients, // from underlying viewids
			final Interval fusionInterval,  // already adjusted for anisotropy???
			final T type,
			final int[] blockSize )
	{
		// assemble all underlying viewIds (which will expand the list of splitViews to all the underlying viewids consist of)
		final List< ViewId > underlyingViewIds = underlyingViewIds( splitViewIdsInput, splitImgLoader.new2oldSetupId() );
		final HashMap<Integer, List<Integer>> old2newSetupId = old2newSetupId( splitImgLoader.new2oldSetupId() );
		final List< ViewId > splitViewIds = splitViewIds( underlyingViewIds, old2newSetupId );

		if ( BlkAffineFusion.is2d( splitViewIds, splitViewDescriptions ) )
			throw new UnsupportedOperationException( "BlkThinPlateSplineFusion: 2D fusion not supported." );

		final SequenceDescription underlyingSD = splitImgLoader.underlyingSequenceDescription();

		System.out.println( "Split viewIds: " + splitViewIdsInput.size() );
		System.out.println( "Underlying viewIds: " + underlyingViewIds.size() );
		System.out.println( "Complete split viewIds: " + splitViewIds.size() );

		//final HashMap< ViewId, Dimensions > viewDimensions = LazyFusionTools.assembleDimensions( underlyingViewIds, viewDescriptions );
		//final Interpolation interpolation = ( interpolationMethod == 1 ) ? NLINEAR : NEARESTNEIGHBOR;

		// to be able to use the "lowest ViewId" wins strategy
		final List< ? extends ViewId > sortedUnderlyingViewIds = new ArrayList<>( underlyingViewIds );

		if ( fusionMap == null || fusionMap.size() == 0 )
			Collections.sort( sortedUnderlyingViewIds );
		else
			Collections.sort( sortedUnderlyingViewIds, (c1,c2) -> Integer.compare( fusionMap.get( c1.getViewSetupId() ), fusionMap.get( c2.getViewSetupId() ) ) );

		// Which split views to process (use un-altered bounding box and registrations).
		// Final filtering happens per Cell.
		// Here we just pre-filter everything outside the fusionInterval.
		final Map<ViewId, AffineTransform3D> splitViewModels =
				splitViewRegistrations.entrySet().stream().collect( Collectors.toMap( e -> e.getKey(), e -> e.getValue().getModel() ) );

		final Overlap splitOverlap = new Overlap(
				splitViewIds,
				splitViewModels,
				LazyFusionTools.assembleDimensions( splitViewIds, splitViewDescriptions ),
				defaultExpansion,
				3 )
				.filter( fusionInterval )
				.offset( fusionInterval.minAsLongArray() );

		// all split views that overlap with the bounding box are mapped to the underlying views
		final List<ViewId> overlappingUnderlyingViewIds = underlyingViewIds( splitOverlap.getViewIds(), splitImgLoader.new2oldSetupId() );

		final List< BlockSupplier< FloatType > > images = new ArrayList<>( overlappingUnderlyingViewIds.size() );

		for ( final ViewId underlyingViewId : overlappingUnderlyingViewIds )
		{
			// ignore downsampling for now
			final Pair<double[][], double[][]> coeff =
					getCoefficients(splitImgLoader, old2newSetupId, splitViewRegistrations, underlyingViewId, Double.NaN, Double.NaN );

			final Coefficients coefficients =
					(intensityAdjustmentCoefficients == null) ? null : intensityAdjustmentCoefficients.get( underlyingViewId );

			final RandomAccessibleInterval inputImg =
					//DownsampleTools.openDownsampled( under, viewId, model, usedDownsampleFactors );
					splitImgLoader.getUnderlyingImgLoader().getSetupImgLoader( underlyingViewId.getViewSetupId() ).getImage( underlyingViewId.getTimePointId() );

			BlockSupplier< FloatType > blocks = BlockSupplier.of( extendInput( inputImg ) )
					.andThen( Convert.convert( new FloatType() ) );

			if ( coefficients != null )
				blocks = blocks.andThen( FastLinearIntensityMap.linearIntensityMap( coefficients, inputImg ) );

			// TODO: we should re-use the thin plate spline coordinate transformations for image and weights
			blocks = blocks.andThen( new TPSImageTransform( fusionInterval, coeff.getA(), coeff.getB(), null ) );

			images.add( blocks );
		}

		return null;
	}

	private static class TPSImageTransform implements UnaryBlockOperator<FloatType, FloatType>
	{
		final Interval boundingBox;
		final double[][] source, target;
		final ThinplateSplineTransform transform;

		public TPSImageTransform(
				final Interval boundingBox,
				final double[][] source,
				final double[][] target,
				final ThinplateSplineTransform transform )
		{
			this.boundingBox = boundingBox;
			this.source = source;
			this.target = target;

			if ( transform == null )
				this.transform = new ThinplateSplineTransform( target, source ); // we go from output to input
			else
				this.transform = transform.copy();
		}

		@Override
		public void compute( final BlockSupplier<FloatType> src, final Interval interval, final Object dest )
		{
			final BlockInterval blockInterval =
					BlockInterval.asBlockInterval(
							Intervals.translate( interval, boundingBox.minAsLongArray() ) );

			final float[] fdest = Cast.unchecked( dest );
			final int[] size = blockInterval.size();
			final int len = safeInt( Intervals.numElements( size ) );

			// figure out the interval we need to fetch from the src image
			final RealInterval srcRealInterval = transform.boundingInterval( blockInterval, IntervalSamplingMethod.CORNERS );
			final Interval srcInterval = Intervals.expand( Intervals.smallestContainingInterval( srcRealInterval ), defaultExpansion );

			// check that the transformed interval is overlapping with the src image first
			if ( Intervals.isEmpty( Intervals.intersect( blockInterval, srcInterval ) ) )
			{
				// TODO: is that necessary?
				Arrays.fill( fdest, 0f );

				return;
			}

			// request the required src data as a copy and translate it to its actual position
			final RandomAccessibleInterval< FloatType > img =
					Views.translate( BlockSupplierUtils.arrayImg( src, srcInterval ), srcInterval.minAsLongArray() );

			// get an interpolator for the copied block
			final RealRandomAccessibleView< FloatType > interp =
					img.view().extend(Extension.zero()).interpolate(Interpolation.clampingNLinear());

			// get a cursor over the srcInterval and a realrandomaccess for the interpolator
			final Cursor<Localizable> cursor = Views.flatIterable( Intervals.positions( srcInterval ) ).cursor();
			final RealRandomAccess< FloatType > rra = interp.realRandomAccess();

			for ( int x = 0; x < len; ++x )
			{
				rra.setPosition( cursor.next() );
				fdest[ x ] = rra.get().get();
			}
		}

		private static final FloatType type = new FloatType();

		@Override
		public FloatType getSourceType() { return type; }

		@Override
		public FloatType getTargetType() { return type; }

		@Override
		public int numSourceDimensions() { return 3; }

		@Override
		public int numTargetDimensions() { return 3; }

		@Override
		public UnaryBlockOperator<FloatType, FloatType> independentCopy()
		{
			return new TPSImageTransform( boundingBox, source, target, transform );
		}
		
	}

	public static Pair< double[][], double[][] > getCoefficients(
			final SplitViewerImgLoader splitImgLoader,
			final HashMap<Integer, List<Integer>> old2newSetupId,
			final Map<ViewId, ViewRegistration> splitRegMap,
			final ViewId underlyingViewId,
			final double anisotropyFactor,
			final double downsampling )
	{
		final List<Integer> splitSetupIds = old2newSetupId.get( underlyingViewId.getViewSetupId() );

		final double[][] source = new double[3][splitSetupIds.size()];
		final double[][] target = new double[3][splitSetupIds.size()];

		for ( int i = 0; i < splitSetupIds.size(); ++i )
		{
			final int splitViewSetupId = splitSetupIds.get( i );
			final ViewId splitViewId = new ViewId( underlyingViewId.getTimePointId(), splitViewSetupId );

			//System.out.println( "\tProcessing splitViewId: " + Group.pvid( splitViewId ) + ":" );

			final ViewRegistration vr = splitRegMap.get( splitViewId );
			final List<ViewTransform> vrList = vr.getTransformList();

			// just making sure this is the split transform
			if ( !vrList.get( vrList.size() - 1).getName().equals(SplittingTools.IMAGE_SPLITTING_NAME) )
				throw new RuntimeException( "First transformation is not " + SplittingTools.IMAGE_SPLITTING_NAME + " for " + Group.pvid( splitViewId ) + ", stopping." );

			// this transformation puts the Zero-Min View of the underlying image where it actually is
			final ViewTransform splitTransform = vrList.get( vrList.size() - 1);
			//System.out.println( "\t" + SplittingTools.IMAGE_SPLITTING_NAME + " transformation: " + splitTransform );

			// get the remaining model
			vr.updateModel();
			final AffineTransform3D model = vr.getModel().copy();

			// preserve anisotropy
			if ( !Double.isNaN( anisotropyFactor ) )
				TransformVirtual.scaleTransform( model, new double[] { 1.0, 1.0, 1.0/anisotropyFactor } );

			// downsampling
			if ( !Double.isNaN( downsampling ) )
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );

			// create a point in the middle of the Zero-Min View and the corresponding point in the global output space
			final Interval splitInterval = splitImgLoader.newSetupId2Interval().get( splitViewSetupId );

			final double[] p = new double[] { splitInterval.dimension( 0 ) / 2.0, splitInterval.dimension( 1 ) / 2.0, splitInterval.dimension( 2 ) / 2.0 };
			final double[] q = new double[ p.length ];
			model.apply( p, q );

			for ( int d = 0; d < p.length; ++d )
			{
				p[ d ] += splitTransform.asAffine3D().get(d, 3); // add the translation offsets of each split view
				source[ d ][ i ] = p[ d ];
				target[ d ][ i ] = q[ d ];
			}

			//System.out.println( "\tCenter point: " + Arrays.toString( p ) + " maps into global output space to: " + Arrays.toString( q ) );
		}

		return new ValuePair<>( source, target );
	}

	public static List<ViewId> underlyingViewIds( final Collection< ? extends ViewId > splitViewIds, final HashMap< Integer, Integer > new2oldSetupId )
	{
		return splitViewIds.stream()
				.map( splitViewId ->
					new ViewId( splitViewId.getTimePointId(), new2oldSetupId.get( splitViewId.getViewSetupId() ) ) )
				.distinct()
				.collect( Collectors.toList());
	}

	public static List<ViewId> splitViewIds( final Collection< ? extends ViewId > underlyingViewIds, final HashMap< Integer, List<Integer>> old2newSetupId )
	{
		return underlyingViewIds.stream().flatMap( underlyingViewId ->
			old2newSetupId.get( underlyingViewId.getViewSetupId() ).stream().map( splitSetupId ->
				new ViewId( underlyingViewId.getTimePointId(), splitSetupId ) )
		).collect( Collectors.toList());
	}

	public static HashMap<Integer, List<Integer>> old2newSetupId( final HashMap<Integer, Integer> new2oldSetupId )
	{
		final HashMap<Integer, List<Integer>> old2newSetupId = new HashMap<>();

		new2oldSetupId.forEach( (k,v) -> old2newSetupId.computeIfAbsent( v, newKey -> new ArrayList<>() ).add( k ) );

		return old2newSetupId;
	}

	private static < T extends NativeType< T > > RandomAccessible< T > extendInput(
			final RandomAccessible< T > input )
	{
		if ( input instanceof IntervalView )
		{
			return extendInput( ( ( IntervalView< T > ) input ).getSource() );
		}
//		else if ( input instanceof MixedTransformView )
//		{
//			final MixedTransformView< T > view = ( MixedTransformView< T > ) input;
//			return new MixedTransformView<>( extendInput( view.getSource() ), view.getTransformToSource() );
//		}
		else if ( input instanceof RandomAccessibleInterval )
		{
			return Views.extendBorder( ( RandomAccessibleInterval< T > ) input );
		}
		else
		{
			// must already be extended then ...
			return input;
		}
	}

}
