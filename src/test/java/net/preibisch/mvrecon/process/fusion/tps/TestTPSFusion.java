package net.preibisch.mvrecon.process.fusion.tps;

import static net.imglib2.util.Util.safeInt;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import bdv.ViewerImgLoader;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.blocks.AbstractBlockSupplier;
import net.imglib2.algorithm.blocks.BlockAlgoUtils;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.blocks.BlockInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.imglib2.view.fluent.RandomAccessibleIntervalView.Extension;
import net.imglib2.view.fluent.RandomAccessibleView.Interpolation;
import net.imglib2.view.fluent.RealRandomAccessibleView;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.splitting.SplittingTools;

/**
 * This needs a minimal grid size of 2x2x2, otherwise we get 'funny' transformations
 */
public class TestTPSFusion
{
	public static ViewerImgLoader getUnderlyingImageLoader( final SpimData2 data )
	{
		if ( SplitViewerImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
			return ( ( SplitViewerImgLoader ) data.getSequenceDescription().getImgLoader() ).getUnderlyingImgLoader();
		else
			return null;
	}

	public static HashMap< ViewId, Pair< double[][], double[][] > > getCoefficients(
			final SpimData2 data,
			final Collection< ViewId > underlyingViewsToProcess,
			final double anisotropyFactor,
			final double downsampling )
	{
		final SplitViewerImgLoader splitImgLoader = ( SplitViewerImgLoader ) data.getSequenceDescription().getImgLoader();

		final HashMap<Integer, Integer> new2oldSetupId = splitImgLoader.new2oldSetupId();
		final HashMap<Integer, Interval> newSetupId2Interval = splitImgLoader.newSetupId2Interval();
		final Map<ViewId, ViewRegistration> viewRegMap = data.getViewRegistrations().getViewRegistrations();

		final List< Integer > underlyingViewSetupsToProcess = underlyingViewsToProcess.stream().map( v -> v.getViewSetupId() ).distinct().collect( Collectors.toList() );
		final HashMap<Integer, List<Integer>> old2newSetupId = old2newSetupId( new2oldSetupId );

		underlyingViewSetupsToProcess.forEach( v -> System.out.println( "ViewSetup " + v + " was split into setup id's: " + Arrays.toString( old2newSetupId.get( v ).toArray() )));

		final HashMap< ViewId, Pair< double[][], double[][] > > underlyingViewId2TPSCoefficients = new HashMap<>();

		for ( final ViewId underlyingViewId : underlyingViewsToProcess )
		{
			System.out.println( "\nProcessing underlyingViewId: " + Group.pvid( underlyingViewId ) + ", which was split into " + old2newSetupId.get( underlyingViewId.getViewSetupId() ).size() + " pieces." );

			final List<Integer> splitSetupIds = old2newSetupId.get( underlyingViewId.getViewSetupId() );

			final double[][] source = new double[3][splitSetupIds.size()];
			final double[][] target = new double[3][splitSetupIds.size()];

			for ( int i = 0; i < splitSetupIds.size(); ++i )
			{
				final int splitViewSetupId = splitSetupIds.get( i );
				final ViewId splitViewId = new ViewId( underlyingViewId.getTimePointId(), splitViewSetupId );

				//System.out.println( "\tProcessing splitViewId: " + Group.pvid( splitViewId ) + ":" );

				final ViewRegistration vr = viewRegMap.get( splitViewId );
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
				final Interval splitInterval = newSetupId2Interval.get( splitViewSetupId );

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

			underlyingViewId2TPSCoefficients.put( underlyingViewId, new ValuePair<>( source, target ) );

			System.out.println( "source: " + Arrays.deepToString( source ) );
			System.out.println( "target: " + Arrays.deepToString( target ) );
		}

		return underlyingViewId2TPSCoefficients;
	}

	public static HashMap<Integer, List<Integer>> old2newSetupId( final HashMap<Integer, Integer> new2oldSetupId )
	{
		final HashMap<Integer, List<Integer>> old2newSetupId = new HashMap<>();

		new2oldSetupId.forEach( (k,v) -> old2newSetupId.computeIfAbsent( v, newKey -> new ArrayList<>() ).add( k ) );

		return old2newSetupId;
	}

	public static List<ViewId> splitViewIds( final List< ViewId > underlyingViewIds, final HashMap< Integer, List<Integer>> old2newSetupId )
	{
		return underlyingViewIds.stream().flatMap( underlyingViewId ->
			old2newSetupId.get( underlyingViewId.getViewSetupId() ).stream().map( splitSetupId ->
				new ViewId( underlyingViewId.getTimePointId(), splitSetupId ) )
		).collect( Collectors.toList());
	}

	public static void main( String[] args ) throws SpimDataException
	{
		final SpimData2 data = 
				new XmlIoSpimData2().load(
						URI.create("file:/Users/preibischs/SparkTest/Stitching/dataset.split.xml") );

		final ViewerImgLoader underlyingImgLoader = getUnderlyingImageLoader(data);

		if ( underlyingImgLoader == null )
			return;

		final SplitViewerImgLoader splitImgLoader = ( SplitViewerImgLoader ) data.getSequenceDescription().getImgLoader();
		final SequenceDescription underlyingSD = splitImgLoader.underlyingSequenceDescription();

		// get all underlying ViewIds with channelId == 0
		final List< ViewId > underlyingViewIds = 
				underlyingSD.getViewDescriptions().values().stream()
				.filter( vd -> vd.isPresent() )
				.filter( vd -> vd.getViewSetup().getChannel().getId() == 0 /*&& vd.getViewSetupId() == 0*/ )
				.map( vd -> new ViewId(vd.getTimePointId(), vd.getViewSetupId() ) )
				.collect( Collectors.toList() );

		if ( underlyingViewIds.size() == 0 )
		{
			System.out.println( "No views remaining. stopping." );
			return;
		}

		// get all split ViewIds for the set of underlying ViewIds
		final List<ViewId> splitViewIds = splitViewIds( underlyingViewIds, old2newSetupId( splitImgLoader.new2oldSetupId() ) );

		System.out.println( "Split viewIds: " + splitViewIds.size() );

		final double downsampling = Double.NaN;
		final double anisotropyFactor = TransformationTools.getAverageAnisotropyFactor( data, underlyingViewIds );

		final HashMap< ViewId, Pair< double[][], double[][] > > coeff =
				getCoefficients( data, underlyingViewIds, anisotropyFactor, downsampling );

		// we estimate the bounding box using the split imagel loader, which will be closer to real bounding box
		BoundingBox boundingBox = new BoundingBoxMaximal( splitViewIds, data ).estimate( "Full Bounding Box" );
		System.out.println( boundingBox );

		if ( !Double.isNaN( anisotropyFactor ) )
		{
			// prepare downsampled boundingbox
			final long[] minBB = boundingBox.minAsLongArray();
			final long[] maxBB = boundingBox.maxAsLongArray();
	
			minBB[ 2 ] = Math.round( Math.floor( minBB[ 2 ] / anisotropyFactor ) );
			maxBB[ 2 ] = Math.round( Math.ceil( maxBB[ 2 ] / anisotropyFactor ) );
	
			boundingBox = new BoundingBox( new FinalInterval( minBB, maxBB ) );
			System.out.println( boundingBox );
		}

		new ImageJ();

		// show a single first image transformed
		/*
		final ThinplateSplineTransform transform = new ThinplateSplineTransform(
				coeff.get( new ViewId( 0, 0 )).getB(),
				coeff.get( new ViewId( 0, 0 )).getA() );

		final RandomAccessibleInterval img = underlyingImgLoader.getSetupImgLoader( 0 ).getImage( 0 );
		final RealRandomAccessibleView interp = 
				img.view().extend(Extension.zero()).interpolate(Interpolation.clampingNLinear());
		final RandomAccessibleInterval< UnsignedByteType > tformedImg =
				new RealTransformRealRandomAccessible<>(interp, transform).realView().raster().interval(boundingBox);
		ImageJFunctions.show( img );
		ImageJFunctions.show(tformedImg);
		*/

		// use BlockSupplier
		final TPSMaxFusionBlockSupplier tpsSupplier = new TPSMaxFusionBlockSupplier( boundingBox, coeff, underlyingImgLoader );

		CachedCellImg<FloatType, ?> fused =
				BlockAlgoUtils.cellImg( tpsSupplier, boundingBox.dimensionsAsLongArray(), new int[] { 256, 256, 1 } );

		ImageJFunctions.show( fused, Executors.newFixedThreadPool( 8 ) );
	}

	private static class TPSMaxFusionBlockSupplier extends AbstractBlockSupplier< FloatType >
	{
		final Interval boundingBox;
		final HashMap< ViewId, Pair< double[][], double[][] > > coeff;
		final BasicImgLoader imgLoader;

		final HashMap< ViewId, RandomAccessible > transformed;

		public TPSMaxFusionBlockSupplier(
				final Interval boundingBox,
				final HashMap< ViewId, Pair< double[][], double[][] > > coeff,
				final BasicImgLoader imgLoader )
		{
			this.boundingBox = boundingBox;
			this.coeff = coeff;
			this.imgLoader = imgLoader;
			this.transformed = new HashMap<>();

			this.coeff.forEach( ( v,c ) -> {

				final ThinplateSplineTransform transform = new ThinplateSplineTransform(
					// we go from output to input
					c.getB(),
					c.getA() );

				final RandomAccessibleInterval img = imgLoader.getSetupImgLoader( v.getViewSetupId() ).getImage( v.getTimePointId() );
				final RealRandomAccessibleView< UnsignedByteType > interp =
						img.view().extend(Extension.zero()).interpolate(Interpolation.clampingNLinear());

				final RandomAccessible tformedImg =
						new RealTransformRealRandomAccessible(interp, transform).realView().raster();

				transformed.put(v, tformedImg);
			});
			
		}

		@Override
		public void copy( final Interval interval, final Object dest )
		{
			final BlockInterval blockInterval =
					BlockInterval.asBlockInterval(
							Intervals.translate( interval, boundingBox.minAsLongArray() ) );

			final int[] size = blockInterval.size();
			final int len = safeInt( Intervals.numElements( size ) );

			transformed.forEach( (v,ra) -> {

				//System.out.println( Util.printInterval( blockInterval ) );
				final Cursor<RealType> c = Views.flatIterable( Views.interval( ra, blockInterval ) ).cursor();

				final float[] fdest = Cast.unchecked( dest );

				for ( int x = 0; x < len; ++x )
					fdest[ x ] = Math.max( c.next().getRealFloat(), fdest[ x ] );
			});
		}

		@Override
		public BlockSupplier<FloatType> independentCopy() { return new TPSMaxFusionBlockSupplier( boundingBox, coeff, imgLoader); }

		private static final FloatType type = new FloatType();

		@Override
		public FloatType getType() { return type; }

		@Override
		public int numDimensions() { return 3; }
	}
}