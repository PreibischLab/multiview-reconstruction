package net.preibisch.mvrecon.process.fusion.tps;

import static net.imglib2.util.Util.safeInt;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import bdv.ViewerImgLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.blocks.AbstractBlockSupplier;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.blocks.BlockInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.fluent.RealRandomAccessibleView;
import net.imglib2.view.fluent.RandomAccessibleIntervalView.Extension;
import net.imglib2.view.fluent.RandomAccessibleView.Interpolation;
import net.preibisch.mvrecon.fiji.plugin.Split_Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.splitting.SplittingTools;
import util.BlockSupplierUtils;
import util.Grid;

public class TPSFusion
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

		final SequenceDescription splitSD = data.getSequenceDescription();
		final SequenceDescription underlyingSD = splitImgLoader.underlyingSequenceDescription();

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

			//double[][] points = new double[][]{
			//	{sx / 2, 0, sx, 0, sx}, // x
			//	{sy / 2, 0, 0, sy, sy}  // y };

			final double[][] source = new double[3][splitSetupIds.size()];
			final double[][] target = new double[3][splitSetupIds.size()];

			for ( int i = 0; i < splitSetupIds.size(); ++i )
			{
				final int splitViewSetupId = splitSetupIds.get( i );
				//final ViewSetup splitViewSetup = splitSD.getViewSetups().get( splitViewSetupId );
				final ViewId splitViewId = new ViewId( underlyingViewId.getTimePointId(), splitViewSetupId );

				System.out.println( "\tProcessing splitViewId: " + Group.pvid( splitViewId ) + ":" );

				final ViewRegistration vr = viewRegMap.get( splitViewId );
				vr.updateModel();
				final List<ViewTransform> vrList = vr.getTransformList();

				// just making sure this is the split transform
				if ( !vrList.get( vrList.size() - 1).getName().equals(SplittingTools.IMAGE_SPLITTING_NAME) )
					throw new RuntimeException( "First transformation is not " + SplittingTools.IMAGE_SPLITTING_NAME + " for " + Group.pvid( splitViewId ) + ", stopping." );

				// this transformation puts the Zero-Min View of the underlying image where it actually is
				System.out.println( "\t" + SplittingTools.IMAGE_SPLITTING_NAME + " transformation: " + vrList.get( vrList.size() - 1).asAffine3D() );

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
					source[ d ][ i ] = p[ d ];
					target[ d ][ i ] = q[ d ];
				}

				System.out.println( "\tCenter point: " + Arrays.toString( p ) + " maps into global output space to: " + Arrays.toString( q ) );
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
				.filter( vd -> vd.getViewSetup().getChannel().getId() == 0 )
				.map( vd -> new ViewId(vd.getTimePointId(), vd.getViewSetupId() ) )
				.collect( Collectors.toList() );

		if ( underlyingViewIds.size() == 0 )
		{
			System.out.println( "No views remaining. stopping." );
			return;
		}

		// get all split ViewIds for the set of underlying ViewIds
		final List<ViewId> splitViewIds = splitViewIds( underlyingViewIds, old2newSetupId( splitImgLoader.new2oldSetupId() ) );

		final double downsampling = Double.NaN;
		final double anisotropyFactor = TransformationTools.getAverageAnisotropyFactor( data, underlyingViewIds );

		final HashMap< ViewId, Pair< double[][], double[][] > > coeff =
				getCoefficients( data, underlyingViewIds, anisotropyFactor, downsampling );

		// we estimate the bounding box using the split imagel loader, which will be closer to real bounding box
		BoundingBox boundingBox = new BoundingBoxMaximal( splitViewIds, data ).estimate( "Full Bounding Box" );
		System.out.println( boundingBox );

		// prepare downsampled boundingbox
		final long[] minBB = boundingBox.minAsLongArray();
		final long[] maxBB = boundingBox.maxAsLongArray();

		minBB[ 2 ] = Math.round( Math.floor( minBB[ 2 ] / anisotropyFactor ) );
		maxBB[ 2 ] = Math.round( Math.ceil( maxBB[ 2 ] / anisotropyFactor ) );

		boundingBox = new BoundingBox( new FinalInterval(minBB, maxBB) );

		System.out.println( boundingBox );

		// using bigger blocksizes than being stored for efficiency (needed for very large datasets)
		final int[] blockSize = new int[] { 128, 128, 64 };
		final int[] blocksPerJob = new int[] { 2, 2, 1 };

		final int[] computeBlockSize = new int[ 3 ];
		Arrays.setAll( computeBlockSize, d -> blockSize[ d ] * blocksPerJob[ d ] );
		final List<long[][]> grid = Grid.create(boundingBox.dimensionsAsLongArray(),
				computeBlockSize,
				blockSize);

		System.out.println( "numJobs = " + grid.size() );

		final TPSFusionBlockSupplier tpsSupplier = new TPSFusionBlockSupplier( coeff, underlyingSD.getImgLoader() );

		
	}

	private static class TPSFusionBlockSupplier extends AbstractBlockSupplier< UnsignedByteType >
	{
		final HashMap< ViewId, Pair< double[][], double[][] > > coeff;
		final ImgLoader imgLoader;

		final HashMap< ViewId, RandomAccessible > transformed;

		public TPSFusionBlockSupplier(
				final HashMap< ViewId, Pair< double[][], double[][] > > coeff,
				final ImgLoader imgLoader )
		{
			this.coeff = coeff;
			this.imgLoader = imgLoader;
			this.transformed = new HashMap<>();

			this.coeff.forEach( (v,c ) ->{
				
				final ThinplateSplineTransform transform = new ThinplateSplineTransform(
					// we go from output to input
					c.getB(),
					c.getA() );

				final RandomAccessibleInterval img = imgLoader.getSetupImgLoader( v.getViewSetupId() ).getImage( v.getTimePointId() );
				final RealRandomAccessibleView< UnsignedByteType > interp =
						img.view().extend(Extension.zero()).interpolate(Interpolation.nLinear());

				final RandomAccessible tformedImg =
						new RealTransformRealRandomAccessible(interp, transform).realView().raster();//.interval(img);

				transformed.put(v, tformedImg);
			});
			
		}

		@Override
		public void copy( final Interval interval, final Object dest )
		{
			final BlockInterval blockInterval = BlockInterval.asBlockInterval( interval );
			final long[] srcPos = blockInterval.min();
			final int[] size = blockInterval.size();
			final int len = safeInt( Intervals.numElements( size ) );

			
		}

		@Override
		public BlockSupplier<UnsignedByteType> independentCopy() { return new TPSFusionBlockSupplier(coeff, imgLoader); }

		private static final UnsignedByteType type = new UnsignedByteType();

		@Override
		public UnsignedByteType getType() { return type; }

		@Override
		public int numDimensions() { return 3; }
	}
}
