package net.preibisch.mvrecon.process.fusion.intensity;

import mpicbg.models.PointMatch;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FastAffineModel1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FlattenedMatches;

import java.util.Collection;

interface IntensityMatchingFilter {

    FastAffineModel1D model();

    void filter(FlattenedMatches candidates, Collection<PointMatch> reducedMatches);
}
