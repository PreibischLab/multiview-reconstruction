package net.preibisch.mvrecon.process.fusion.tps;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import bdv.ViewerImgLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI.FusionType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.fusion.blk.BlkThinPlateSplineFusion;

public class TestBlkThinPlateSplineFusion
{

	public static void main( String[] args ) throws SpimDataException
	{
		final SpimData2 data = 
				new XmlIoSpimData2().load(
						URI.create("file:/Users/preibischs/SparkTest/Stitching/dataset.split.xml") );

		final ViewerImgLoader underlyingImgLoader = TestTPSFusion.getUnderlyingImageLoader(data);

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

		BlkThinPlateSplineFusion.init(
				null,//final Converter< FloatType, T > converter,
				splitImgLoader,
				splitViewIds,
				data.getViewRegistrations().getViewRegistrations(), // already adjusted for anisotropy
				data.getSequenceDescription().getViewDescriptions(),
				FusionType.MAX_INTENSITY,
				Double.NaN,
				null,
				null,
				boundingBox,  // already adjusted for anisotropy???
				new UnsignedByteType() );
	}
}
