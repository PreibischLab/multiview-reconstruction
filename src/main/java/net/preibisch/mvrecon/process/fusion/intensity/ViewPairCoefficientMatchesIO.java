package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import mpicbg.spim.data.sequence.ViewId;

public class ViewPairCoefficientMatchesIO {

	/**
	 * Reduced matches are read/written from/to text files in this directory
	 */
	private final String directory;

	public ViewPairCoefficientMatchesIO(final String directory) {
		this.directory = directory;
	}

	// TODO remove?
	public void write(
			final ViewId p1,
			final ViewId p2,
			final List<IntensityMatcher.CoefficientMatch> coefficientMatches
	) throws IOException {
		final String fn = getFilename(p1, p2);
		try (final IntensityMatchesIO.Writer output = new IntensityMatchesIO.Writer(fn)) {
			output.writeViewId(p1);
			output.writeViewId(p2);
			for (final IntensityMatcher.CoefficientMatch m : coefficientMatches) {
				output.writeMatches(m);
			}
		}
	}

	public void write(
			ViewPairCoefficientMatches matches
	) throws IOException {
		write(matches.view1(), matches.view2(), matches.coefficientMatches());
	}

	public ViewPairCoefficientMatches read(
			final ViewId p1,
			final ViewId p2
	) throws IOException {
		final String fn = getFilename(p1, p2);
		return readFromFile(fn);
	}

	private static ViewPairCoefficientMatches readFromFile(final String fn)
			throws IOException {
		if (Paths.get(fn).toFile().isFile()) {
			try (final IntensityMatchesIO.Reader input = new IntensityMatchesIO.Reader(fn)) {
				final ViewId p1 = input.readViewId();
				final ViewId p2 = input.readViewId();
				List<IntensityMatcher.CoefficientMatch> coefficientMatches = new ArrayList<>();
				IntensityMatcher.CoefficientMatch match;
				while ((match = input.readMatches()) != null) {
					coefficientMatches.add(match);
				}
				return new ViewPairCoefficientMatches(p1, p2, coefficientMatches);
			}
		}
		return null;
	}

	private String getFilename(final ViewId p1, final ViewId p2) {
		return String.format("%s/t%d_s%d--t%d_s%d.txt",
				directory,
				p1.getTimePointId(), p1.getViewSetupId(),
				p2.getTimePointId(), p2.getViewSetupId());
	}
}
