package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

import mpicbg.spim.data.sequence.ViewId;

/**
 * The simplest way to group interest points, just add them all (this will create problems in overlaps)
 * 
 * @author spreibi
 */
public class InterestPointGroupingAll< V extends ViewId > extends InterestPointGrouping< V >
{
	public InterestPointGroupingAll( final Map< V, List< InterestPoint > > interestpoints )
	{
		super( interestpoints );
	}

	@Override
	protected List< GroupedInterestPoint< V > > merge( final Map< V, List< InterestPoint > > toMerge )
	{
		return mergeAll( toMerge );
	}

	public static < V extends ViewId > ArrayList< GroupedInterestPoint< V > > mergeAll( final Map< V, List< InterestPoint > > toMerge )
	{
		final ArrayList< GroupedInterestPoint< V > > grouped = new ArrayList<>();

		for ( final V view : toMerge.keySet() )
		{
			final List< InterestPoint > pointList = toMerge.get( view );

			for ( final InterestPoint p : pointList )
			{
				grouped.add( new GroupedInterestPoint< V >( view, p.getId(), p.getL() ) );
			}
		}

		return grouped;
	}
}
