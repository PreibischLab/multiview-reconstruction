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
	 * @param candidates candidate data points including (many) outliers
	 * @param iterations number of iterations
	 * @param maxEpsilon maximal allowed transfer error
	 * @param minInlierRatio minimal number of inliers to number of
	 *   candidates
	 * @param minNumInliers minimally required absolute number of inliers
	 * @param maxTrust reject candidates with a cost larger than
	 *   maxTrust * median cost
	 *
	 * @return indices of {@code candidates} that are inliers, or {@code null}
	 * if the model could not be fitted (model parameters remain unchanged in
	 * that case).
	 */
	MatchIndices fastFilterRansac(
			final FlattenedMatches candidates,
			final int iterations,
			final double maxEpsilon,
			final double minInlierRatio,
			final int minNumInliers,
			final double maxTrust )
			throws NotEnoughDataPointsException;
}
