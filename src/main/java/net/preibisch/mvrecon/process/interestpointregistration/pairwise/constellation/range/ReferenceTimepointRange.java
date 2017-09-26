package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;

public class ReferenceTimepointRange< V extends ViewId > implements RangeComparator< V >
{
	final int referenceTimepoint;

	public ReferenceTimepointRange( final int referenceTimepoint )
	{
		this.referenceTimepoint = referenceTimepoint;
	}
	public ReferenceTimepointRange( final TimePoint referenceTimepoint )
	{
		this( referenceTimepoint.getId() );
	}

	@Override
	public boolean inRange( final V view1, final V view2 )
	{
		// if one of the views is a reference timepoint or if they are from the same timepoint (fixed views are discarded later)
		if ( view1.getTimePointId() == referenceTimepoint || view2.getTimePointId() == referenceTimepoint || view1.getTimePointId() == view2.getTimePointId() )
			return true;
		else
			return false;
	}

	public int getReferenceTimepointId() { return referenceTimepoint; }
}
