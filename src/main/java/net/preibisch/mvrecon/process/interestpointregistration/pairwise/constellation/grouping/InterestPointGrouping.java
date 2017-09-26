package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Interface for grouping interest points from multiple views that are in one group
 * 
 * @author spreibi
 */
public abstract class InterestPointGrouping< V extends ViewId > implements Grouping< V, List< GroupedInterestPoint< V > > >
{
	// all interestpoints
	final Map< V, List< InterestPoint > > interestpoints;
	int before, after;

	public InterestPointGrouping( final Map< V, List< InterestPoint > > interestpoints )
	{
		this.interestpoints = interestpoints;
	}

	public int countBefore() { return before; }
	public int countAfter() { return after; }

	@Override
	public List< GroupedInterestPoint< V > > group( final Group< V > group )
	{
		final Map< V, List< InterestPoint > > toMerge = new HashMap<>();

		this.before = 0;

		for ( final V view : group )
		{
			final List< InterestPoint > points = interestpoints.get( view );

			if ( points == null )
				throw new RuntimeException( "no interestpoints available" );

			before += points.size();
	
			toMerge.put( view, points );
		}

		final List< GroupedInterestPoint< V > > merged = merge( toMerge );

		this.after = merged.size();

		return merged;
	}

	protected abstract List< GroupedInterestPoint< V > > merge( Map< V, List< InterestPoint > > toMerge );
}
