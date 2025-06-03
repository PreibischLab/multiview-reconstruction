package net.preibisch.mvrecon.process.fusion.intensity.mpicbg;

import java.util.Collection;
import java.util.List;

import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;

// TODO remove?
interface FastModel
{
	/**
	 * Estimate a {@code Model} from a set with many outliers by first filtering
	 * the worst outliers with RANSAC and filter potential outliers by robust
	 * iterative regression.
	 *
	 * @param candidates candidate data points inluding (many) outliers
	 * @param inliers remaining candidates after RANSAC
	 * @param iterations number of iterations
	 * @param maxEpsilon maximal allowed transfer error
	 * @param minInlierRatio minimal number of inliers to number of
	 *   candidates
	 * @param minNumInliers minimally required absolute number of inliers
	 * @param maxTrust reject candidates with a cost larger than
	 *   maxTrust * median cost
	 *
	 * @return true if the model could be estimated and inliers is not empty,
	 * false otherwise.  If false, this model remains unchanged.
	 */
	< P extends PointMatch > boolean fastFilterRansac(
			final List< P > candidates,
			final Collection< P > inliers,
			final int iterations,
			final double maxEpsilon,
			final double minInlierRatio,
			final int minNumInliers,
			final double maxTrust )
			throws NotEnoughDataPointsException;
}
