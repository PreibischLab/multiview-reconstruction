package net.preibisch.mvrecon.process.interestpointregistration.pairwise;

import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

public class LinkedInterestPoint< P > extends InterestPoint
{
	private static final long serialVersionUID = 1L;

	final P link;

	public LinkedInterestPoint( final int id, final double[] l, final boolean useW, final P link )
	{
		super( id, l.clone(), useW );

		this.link = link;
	}

	public LinkedInterestPoint( final int id, final double[] l, final P link )
	{
		this( id, l.clone(), true, link );
	}

	public P getLinkedObject() { return link; }
	
	public String toString() { return "LinkedInterestPoint " + Util.printCoordinates( l ); }
}
