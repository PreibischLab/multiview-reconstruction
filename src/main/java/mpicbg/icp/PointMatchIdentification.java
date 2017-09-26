package mpicbg.icp;


import java.util.List;

import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.RealLocalizable;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.LinkedInterestPoint;

public interface PointMatchIdentification < P extends RealLocalizable >
{
	public List< PointMatchGeneric< LinkedInterestPoint< P > > > assignPointMatches( final List< LinkedInterestPoint< P > > target, final List< LinkedInterestPoint< P > > reference ) throws NoSuitablePointsException;
}
