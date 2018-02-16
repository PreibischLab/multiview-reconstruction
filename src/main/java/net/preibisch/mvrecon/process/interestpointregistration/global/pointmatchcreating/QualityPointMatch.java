package net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating;

import java.util.ArrayList;
import java.util.Collection;

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

	private static void flipQ(
			final Collection< QualityPointMatch > matches,
			final Collection< QualityPointMatch > flippedMatches )
	{
		for ( final QualityPointMatch match : matches )
			flippedMatches.add(
					new QualityPointMatch(
							match.p2,
							match.p1,
							match.getQuality() ) );
	}

	final public static Collection< PointMatch > flipQ( final Collection< PointMatch > matches )
	{
		final ArrayList< QualityPointMatch > list = new ArrayList<>();
		flipQ( (Collection< QualityPointMatch >)(Object)matches, list );
		return (Collection< PointMatch >)(Object)list;
	}
}
