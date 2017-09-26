package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.global;

import java.util.ArrayList;
import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;

public class IndividualTimepointGUI implements GlobalGUI
{
	final SpimData2 data;

	public IndividualTimepointGUI( final SpimData2 data )
	{
		this.data = data;
	}

	public List< List< ViewId > > getIndividualSets( final List< ViewId > viewIds )
	{
		final ArrayList< List< ViewId > > sets = new ArrayList<>();

		for ( final TimePoint timepoint : SpimData2.getAllTimePointsSorted( data, viewIds ) )
		{
			final ArrayList< ViewId > set = new ArrayList<>();

			for ( final ViewId viewId : viewIds )
				if ( viewId.getTimePointId() == timepoint.getId() )
					set.add( viewId );

			sets.add( set );
		}

		return sets;
	}
}
