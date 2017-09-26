package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range;

public interface RangeComparator< V >
{
	public boolean inRange( V view1, V view2 );
}
