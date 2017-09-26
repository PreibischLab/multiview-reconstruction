package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range;

import mpicbg.spim.data.sequence.ViewId;

public class TimepointRange< V extends ViewId > implements RangeComparator< V >
{
	final int maxDistance;

	public TimepointRange( final int maxDistance ) { this.maxDistance = maxDistance; }

	@Override
	public boolean inRange( final V view1, final V view2 )
	{
		if ( Math.abs( view2.getTimePointId() - view1.getTimePointId() ) <= maxDistance )
			return true;
		else
			return false;
	}
}
