package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap;

import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;

public class AllAgainstAllOverlap< V > implements OverlapDetection< V >
{
	final int n;

	public AllAgainstAllOverlap( final int numDimensions ){ this.n = numDimensions; }

	@Override
	public boolean overlaps( final V view1, final V view2 ) { return true; }

	@Override
	public RealInterval getOverlapInterval( final V view1, final V view2 )
	{
		final double[] min = new double[ n ];
		final double[] max = new double[ n ];

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] = 0;
			max[ d ] = 1;
		}

		return new FinalRealInterval( min, max );
	}
}
