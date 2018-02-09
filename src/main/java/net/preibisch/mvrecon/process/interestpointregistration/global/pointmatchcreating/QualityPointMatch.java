package net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;

public class QualityPointMatch extends PointMatch
{
	private static final long serialVersionUID = 1L;

	final double quality;

	public QualityPointMatch( final Point p1, final Point p2, final double quality )
	{
		super( p1, p2 );

		this.quality = quality;
	}

	public double getQuality() { return quality; }
}
