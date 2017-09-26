package mpicbg.icp;

import java.util.ArrayList;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.LinkedInterestPoint;

public class SimplePointMatchIdentification < P extends RealLocalizable > implements PointMatchIdentification< P >
{
	double distanceThresold;

	public SimplePointMatchIdentification( final double distanceThreshold )
	{
		this.distanceThresold = distanceThreshold;
	}

	public SimplePointMatchIdentification()
	{
		this.distanceThresold = Double.MAX_VALUE;
	}

	public void setDistanceThreshold( final double distanceThreshold ) { this.distanceThresold = distanceThreshold; }
	public double getDistanceThreshold() { return this.distanceThresold; }

	@Override
	public ArrayList< PointMatchGeneric< LinkedInterestPoint< P > > > assignPointMatches( final List< LinkedInterestPoint< P > > target, final List< LinkedInterestPoint< P > > reference )
	{
		final ArrayList< PointMatchGeneric< LinkedInterestPoint< P > > > pointMatches = new ArrayList<>();

		final KDTree< LinkedInterestPoint< P > > kdTreeTarget = new KDTree<>( target, target );
		final NearestNeighborSearchOnKDTree< LinkedInterestPoint< P > > nnSearchTarget = new NearestNeighborSearchOnKDTree<>( kdTreeTarget );

		for ( final LinkedInterestPoint< P > point : reference )
		{
			nnSearchTarget.search( point );
			final LinkedInterestPoint< P > correspondingPoint = nnSearchTarget.getSampler().get();

			// world coordinates of point
			if ( Point.distance( correspondingPoint, point ) <= distanceThresold )
				pointMatches.add( new PointMatchGeneric< LinkedInterestPoint< P > >( correspondingPoint, point ) );
		}

		return pointMatches;
	}
}
