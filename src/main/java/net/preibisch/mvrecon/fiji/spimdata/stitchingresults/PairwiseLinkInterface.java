package net.preibisch.mvrecon.fiji.spimdata.stitchingresults;

import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public interface PairwiseLinkInterface
{
	public Set< Pair< Group< ViewId >, Group< ViewId > > > getPairwiseLinks();
}
