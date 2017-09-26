package spim.headless.boundingbox;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.headless.registration.TestRegistration;
import spim.process.boundingbox.BoundingBoxBigDataViewer;
import spim.process.boundingbox.BoundingBoxEstimation;
import spim.process.boundingbox.BoundingBoxMaximal;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestBoundingBox
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testBoundingBox( spimData, true );
	}

	public static BoundingBox testBoundingBox( final SpimData2 spimData, final boolean bdv )
	{
		// run the whole pipeline
		TestRegistration.testRegistration( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		BoundingBoxEstimation estimation;

		if ( bdv )
			estimation = new BoundingBoxBigDataViewer( spimData, viewIds );
		else
			estimation = new BoundingBoxMaximal( viewIds, spimData );

		final BoundingBox bb = estimation.estimate( "Full Bounding Box" );

		System.out.println( bb );

		return bb;
	}
}
