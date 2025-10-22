package net.preibisch.mvrecon.process.fusion.intensity;

import mpicbg.models.PointMatch;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FastAffineModel1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FlattenedMatches;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.RansacRegressionReduceFilter;

import java.util.Collection;

class RansacIntensityMatchingFilter implements IntensityMatchingFilter {

    private final RansacRegressionReduceFilter filter;

    private final FastAffineModel1D model;

    /**
     * @param model          the model to fit
     * @param iterations     number of RANSAC iterations
     * @param maxEpsilon     maximal allowed transfer error
     * @param minInlierRatio minimal number of inliers to number of candidates
     * @param minNumInliers  minimally required absolute number of inliers
     * @param maxTrust       reject candidates with a cost larger than maxTrust * median cost
     */
    public RansacIntensityMatchingFilter(
            final FastAffineModel1D model,
            final int iterations,
            final double maxEpsilon,
            final double minInlierRatio,
            final int minNumInliers,
            final double maxTrust
    ) {
        this.model = model;
        this.filter = new RansacRegressionReduceFilter(model, iterations, maxEpsilon, minInlierRatio, minNumInliers, maxTrust);
    }

    @Override
    public FastAffineModel1D model() {
        return model;
    }

    @Override
    public void filter(FlattenedMatches candidates, Collection<PointMatch> reducedMatches) {
        filter.filter(candidates, reducedMatches);
    }
}
