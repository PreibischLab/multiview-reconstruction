package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import net.imglib2.RealPoint;

public class NonWastefulRealPoint extends RealPoint
{
	public NonWastefulRealPoint( final double[] position, final boolean copy )
	{
		super( position, copy );
	}
}
