package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range;

public class AllInRange< V > implements RangeComparator< V >
{
	@Override
	public boolean inRange( final V view1, final V view2 ) { return true; }
}
