package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.global;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;

public class AllToAllGUI implements GlobalGUI
{
	public List< List< ViewId > > getIndividualSets( final List< ViewId > viewIds )
	{
		final ArrayList< List< ViewId > > sets = new ArrayList<>();
		sets.add( viewIds );

		return sets;
	}
}
