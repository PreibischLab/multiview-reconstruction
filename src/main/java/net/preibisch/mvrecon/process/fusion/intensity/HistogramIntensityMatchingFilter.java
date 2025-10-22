package net.preibisch.mvrecon.process.fusion.intensity;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FastAffineModel1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.FlattenedMatches;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.Point1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.PointMatch1D;

import java.util.Arrays;
import java.util.Collection;

class HistogramIntensityMatchingFilter implements IntensityMatchingFilter {

    private final FastAffineModel1D model;

    public HistogramIntensityMatchingFilter(final FastAffineModel1D model) {
        this.model = model;
    }

    @Override
    public FastAffineModel1D model() {
        return model;
    }

    @Override
    public void filter(final FlattenedMatches candidates, final Collection<PointMatch> reducedMatches) {
        reducedMatches.clear();

        final double[] histo1 = Arrays.copyOf(candidates.p()[0], candidates.size());
        Arrays.sort(histo1);
        final double[] histo2 = Arrays.copyOf(candidates.q()[0], candidates.size());
        Arrays.sort(histo2);

        final int numSamples = 100; // TODO: make this a parameter?
        final FlattenedMatches matches = new FlattenedMatches(1, numSamples);
        matches.setWeighted(false);
        for (int i = 0; i < numSamples; ++i) {
            final double p = histo1[histo1.length * i / numSamples];
            final double q = histo2[histo2.length * i / numSamples];
            matches.put(p, q, 1);
        }
        try {
            model.fit(matches);
        } catch (NotEnoughDataPointsException | IllDefinedDataPointsException e) {
            e.printStackTrace();
            return;
        }

        final double min = histo1[0];
        final double max = histo1[histo1.length - 1];
        reducedMatches.add(new PointMatch1D(new Point1D(min), new Point1D(model.apply(min)), 1.0));
        reducedMatches.add(new PointMatch1D(new Point1D(max), new Point1D(model.apply(max)), 1.0));
    }
}
