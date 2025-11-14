package net.preibisch.mvrecon.process.fusion.blk;

import static net.imglib2.algorithm.blocks.transform.Transform.Interpolation.NEARESTNEIGHBOR;
import static net.imglib2.algorithm.blocks.transform.Transform.Interpolation.NLINEAR;

import java.util.ArrayList;
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
import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.algorithm.blocks.transform.Transform.Interpolation;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI.FusionType;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.process.fusion.intensity.Coefficients;
import net.preibisch.mvrecon.process.fusion.lazy.LazyFusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.splitting.SplittingTools;

public class BlkThinPlateSplineFusion
{

	public static < T extends RealType< T > & NativeType< T > > BlockSupplier< T > init(
			final Converter< FloatType, T > converter,
			final SplitViewerImgLoader splitImgLoader,
			final Collection< ? extends ViewId > splitViewIdsInput,
			final Map< ViewId, ViewRegistration > splitViewRegistrations,
			final Map< ViewId, ? extends BasicViewDescription< ? > > splitViewDescriptions,
			final FusionType fusionType,
			final double anisotropyFactor,
			final Map< Integer, Integer > fusionMap, // old setupId > new setupId for fusion order, only makes sense with FusionType.FIRST_LOW or FusionType.FIRST_HIGH
			final int interpolationMethod,
			final Map< ViewId, Coefficients > intensityAdjustmentCoefficients,
			final Interval fusionInterval,
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
				LazyFusionTools.defaultNonrigidExpansion,
				3 )
				.filter( fusionInterval )
				.offset( fusionInterval.minAsLongArray() );

		final List<ViewId> overlappingUnderlyingViewIds = underlyingViewIds( splitOverlap.getViewIds(), splitImgLoader.new2oldSetupId() );
		final List< BlockSupplier< FloatType > > images = new ArrayList<>( overlappingUnderlyingViewIds.size() );

		for ( final ViewId underlyingViewId : overlappingUnderlyingViewIds )
		{
			final Pair<double[][], double[][]> coeff =
					getCoefficients(splitImgLoader, old2newSetupId, splitViewRegistrations, underlyingViewId, anisotropyFactor, Double.NaN );

			
		}

		return null;
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
}
