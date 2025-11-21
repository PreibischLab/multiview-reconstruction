package net.preibisch.mvrecon.process.fusion.tps;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import bdv.ViewerImgLoader;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.fusion.blk.BlkThinPlateSplineFusion;

public class TPSAssessQualityStepsize
{
	public static void visualize(
			final Interval boundingBox,
			final double[][] source,
			final double[][] target,
			final long[] stepSize )
	{
		visualize(null, boundingBox, source, target, stepSize);
	}

	public static void visualize(
			final Interval sourceImageInterval,
			final Interval boundingBox,
			final double[][] source,
			final double[][] target,
			final long[] stepSize )
	{
		final ThinplateSplineTransform transform = new ThinplateSplineTransform( target, source ); // we go from output to input

		final RandomAccessibleInterval<DoubleType> interpField =
				BlkThinPlateSplineFusion.interpolatedField( transform, boundingBox, stepSize );

		final RandomAccessibleInterval<DoubleType> fullField =
				BlkThinPlateSplineFusion.fullDeformationField( transform, boundingBox );

		if ( sourceImageInterval != null )
		{
			for ( int d = 0; d < 3; ++d )
			{
				final long min = sourceImageInterval.min( d );
				final long max = sourceImageInterval.max( d );

				Views.hyperSlice( interpField, 3, d ).forEach( t -> {
					final double v = t.get();
					if ( v < min || v > max )
						t.set( 0 );
				});

				Views.hyperSlice( fullField, 3, d ).forEach( t -> {
					final double v = t.get();
					if ( v < min || v > max )
						t.set( 0 );
				});
			}
		}

		ImageJFunctions.show( Views.hyperSlice( interpField, 3, 0 ) ).setTitle( "xInterp" );
		ImageJFunctions.show( Views.hyperSlice( fullField, 3, 0 ) ).setTitle( "xFull" );
	}

	public static void main( String[] args ) throws SpimDataException
	{
		final SpimData2 data = 
				new XmlIoSpimData2().load(
						URI.create("file:/Users/preibischs/SparkTest/Stitching/dataset.split.xml") );

		final ViewerImgLoader underlyingImgLoader = BlkThinPlateSplineFusion.getUnderlyingImageLoader(data);

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

		final HashMap<Integer, List<Integer>> old2newSetupId = BlkThinPlateSplineFusion.old2newSetupId( splitImgLoader.new2oldSetupId() );
		final List< ViewId > splitViewIds = BlkThinPlateSplineFusion.splitViewIds( underlyingViewIds, old2newSetupId );

		// we estimate the bounding box using the split imagel loader, which will be closer to real bounding box
		BoundingBox boundingBox = new BoundingBoxMaximal( splitViewIds, data ).estimate( "Full Bounding Box" );
		System.out.println( boundingBox );

		final HashMap< ViewId, Pair< double[][], double[][] > > coeff =
				TestTPSFusion.getCoefficients( splitImgLoader, data.getViewRegistrations().getViewRegistrations(), underlyingViewIds, Double.NaN, Double.NaN );

		new ImageJ();
		final ViewId v = new ViewId( 0, 0 );

		final Interval slice = new FinalInterval(
				boundingBox.minAsLongArray(),
				new long[] { boundingBox.max( 0 ), boundingBox.max( 1 ), boundingBox.min( 2 ) } );

		visualize( slice, coeff.get( v ).getA(), coeff.get( v ).getB(), new long[] { 10, 10, 10 } );
		visualize(
				new FinalInterval( underlyingSD.getViewDescription( v ).getViewSetup().getSize() ),
				slice, coeff.get( v ).getA(), coeff.get( v ).getB(), new long[] { 10, 10, 10 } );
	}
}
