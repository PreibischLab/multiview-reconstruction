package net.preibisch.mvrecon.process.fusion.tps;

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
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Interval;
import net.preibisch.mvrecon.fiji.plugin.Split_Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.splitting.SplittingTools;

public class TPSFusion
{
	public static ViewerImgLoader getUnderlyingImageLoader( final SpimData2 data )
	{
		if ( SplitViewerImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
			return ( ( SplitViewerImgLoader ) data.getSequenceDescription().getImgLoader() ).getUnderlyingImgLoader();
		else
			return null;
	}

	public static void getCoefficients( final SpimData2 data, final Collection< ViewId > underlyingViewsToProcess )
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

		for ( final ViewId underlyingViewId : underlyingViewsToProcess )
		{
			System.out.println( "Processing underlyingViewId: " + Group.pvid( underlyingViewId ) + ", which was split into " + old2newSetupId.get( underlyingViewId.getViewSetupId() ).size() + " pieces." );

			for ( final int splitViewSetupId : old2newSetupId.get( underlyingViewId.getViewSetupId() ) )
			{
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

				// create a point in the middle of the Zero-Min View and the corresponding point in the global output space
				final Interval splitInterval = newSetupId2Interval.get( splitViewSetupId );
				final double[] p = new double[] { splitInterval.dimension( 0 ) / 2.0, splitInterval.dimension( 1 ) / 2.0, splitInterval.dimension( 2 ) / 2.0 };
				final double[] q = new double[ p.length ];
				vr.getModel().apply( p, q );

				System.out.println( "\tCenter point: " + Arrays.toString( p ) + " maps into global output space to: " + Arrays.toString( q ) );
			}

			System.exit( 0 );
		}
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
