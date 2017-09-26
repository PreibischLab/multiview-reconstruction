package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range.TimepointRange;

public class IndividualTimepoints extends AllToAllRange< ViewId, TimepointRange< ViewId > >
{
	public IndividualTimepoints(
			final List< ViewId > views,
			final Set< Group< ViewId > > groups )
	{
		super( views, groups, new TimepointRange< ViewId >( 0 ) );
	}

	public static < V extends ViewId > List< Pair< V, V > > allPairs(
			final List< ? extends V > views,
			final Collection< ? extends Group< V > > groups )
	{
		return AllToAllRange.allPairs( views, groups, new TimepointRange< V >( 0 ) );
	}
}
