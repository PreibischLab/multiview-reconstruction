package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.global;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;

public interface GlobalGUI
{
	/*
	 * If the registration consists of multiple, completely indepedents sets of views, split them up here for later
	 * 
	 * @param viewIds
	 * @return
	 */
	public List< List< ViewId > > getIndividualSets( final List< ViewId > viewIds );
}
