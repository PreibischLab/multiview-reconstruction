package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.MatchPointList;
import spim.process.interestpointregistration.PairwiseMatch;

/**
 * A registration type where each timepoint is registered individually
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IndividualTimepointRegistration extends GlobalOptimizationType
{
	public IndividualTimepointRegistration(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess,
			final boolean save )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess, save, false );
	}

	@Override
	public boolean isFixedTile( final ViewId viewId, final GlobalOptimizationSubset set )
	{
		return fixedTiles.contains( viewId );
		/*// fix first tile
		if ( fixFirstTile && viewId == set.getViews().get( 0 ) )
			return true;
		else
			return false;*/
	}

	@Override
	public List< GlobalOptimizationSubset > assembleAllViewPairs()
	{
		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();
		
		for ( final TimePoint timepoint : timepointsToProcess )
		{
			final HashMap< ViewId, MatchPointList > pointLists = this.getInterestPoints( timepoint );
			
			final ArrayList< ViewId > views = new ArrayList< ViewId >();
			views.addAll( pointLists.keySet() );
			Collections.sort( views );
			
			final ArrayList< PairwiseMatch > viewPairs = new ArrayList< PairwiseMatch >();
			
			// the views of the timepoint that is processed
			// add this only if we do not consider timepoints to be units
			// Note: if considerTimePointsAsUnits == true, there are no pairs
			if ( !considerTimePointsAsUnit )
			{
				for ( int a = 0; a < views.size() - 1; ++a )
					for ( int b = a + 1; b < views.size(); ++b )
					{
						final ViewId viewIdA = views.get( a );
						final ViewId viewIdB = views.get( b );
						
						final MatchPointList listA = pointLists.get( viewIdA );
						final MatchPointList listB = pointLists.get( viewIdB );
						
						viewPairs.add( new PairwiseMatch( viewIdA, viewIdB, listA, listB ) );
					}
			}
			list.add( new GlobalOptimizationSubset( viewPairs, "individual timepoint registration: " + timepoint.getName() + "(id=" + timepoint.getId() + ")" ) );
		}
		
		return list;
	}
}