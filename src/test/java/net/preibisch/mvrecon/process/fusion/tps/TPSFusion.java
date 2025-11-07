package net.preibisch.mvrecon.process.fusion.tps;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import bdv.ViewerImgLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;

public class TPSFusion
{
	public static ViewerImgLoader getUnderlyingImageLoader( final SpimData2 data )
	{
		if ( SplitViewerImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
			return ( ( SplitViewerImgLoader ) data.getSequenceDescription().getImgLoader() ).getUnderlyingImgLoader();
		else
			return null;
	}

	public static void getCoefficients( final SpimData2 data, final Collection< ViewId > underlyingViews )
	{
		final SplitViewerImgLoader imgLoader = ( SplitViewerImgLoader ) data.getSequenceDescription().getImgLoader();
		final SequenceDescription underlyingSD = imgLoader.underlyingSequenceDescription();
		final HashMap<Integer, Integer> new2oldSetupId = imgLoader.new2oldSetupId();
		final HashMap<Integer, Interval> newSetupId2Interval = imgLoader.newSetupId2Interval();

		final HashMap<Integer, List<Integer>> old2newSetupId = old2newSetupId( new2oldSetupId );

		underlyingViews.stream().map( v -> v.getViewSetupId() ).distinct()
			.forEach( v -> System.out.println( "ViewSetup " + v + " was split into setup id's: " + Arrays.toString( old2newSetupId.get( v ).toArray() )));
	}

	public static HashMap<Integer, List<Integer>> old2newSetupId( final HashMap<Integer, Integer> new2oldSetupId )
	{
		final HashMap<Integer, List<Integer>> old2newSetupId = new HashMap<>();

		new2oldSetupId.forEach( (k,v) -> old2newSetupId.computeIfAbsent( v, newKey -> new ArrayList<>() ).add( k ) );

		return old2newSetupId;
	}

	public static void main( String[] args ) throws SpimDataException
	{
		final SpimData2 data = 
				new XmlIoSpimData2().load(
						URI.create("file:/Users/preibischs/SparkTest/Stitching/dataset.split.xml") );

		final ViewerImgLoader underlyingImgLoader = getUnderlyingImageLoader(data);

		if ( underlyingImgLoader == null )
			return;

		final SplitViewerImgLoader imgLoader = ( SplitViewerImgLoader ) data.getSequenceDescription().getImgLoader();

		final SequenceDescription underlyingSD = imgLoader.underlyingSequenceDescription();

		final List< ViewId > viewIds = 
				underlyingSD.getViewDescriptions().values().stream()
				.filter( vd -> vd.getViewSetup().getChannel().getId() == 0 ).collect( Collectors.toList() );

		getCoefficients( data, viewIds );
	}
}
