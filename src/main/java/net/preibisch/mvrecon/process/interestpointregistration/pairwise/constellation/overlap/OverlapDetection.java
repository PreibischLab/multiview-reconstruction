package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap;

import net.imglib2.RealInterval;

public interface OverlapDetection< V >
{
	public boolean overlaps( final V view1, final V view2 );
	public RealInterval getOverlapInterval( final V view1, final V view2 );
}
