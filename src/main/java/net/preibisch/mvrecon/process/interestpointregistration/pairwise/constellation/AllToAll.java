package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range.AllInRange;

public class AllToAll< V extends Comparable< V > > extends AllToAllRange< V, AllInRange< V > >
{
	public AllToAll(
			final List< V > views,
			final Set< Group< V > > groups )
	{
		super( views, groups, new AllInRange<>() );
	}

	public static < V > List< Pair< V, V > > allPairs(
			final List< ? extends V > views,
			final Collection< ? extends Group< V > > groups )
	{
		return AllToAllRange.allPairs( views, groups, new AllInRange<>() );
	}
}
