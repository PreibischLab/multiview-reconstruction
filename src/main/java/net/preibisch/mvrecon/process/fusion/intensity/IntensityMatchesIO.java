package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import mpicbg.models.PointMatch;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.Point1D;
import net.preibisch.mvrecon.process.fusion.intensity.mpicbg.PointMatch1D;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

class IntensityMatchesIO {

	static class Writer implements Closeable {

		private final BufferedWriter writer;

		Writer(final String filePath) throws IOException {
			writer = Files.newBufferedWriter(Paths.get(filePath), CREATE, TRUNCATE_EXISTING);
		}

		void writeViewId(final ViewId viewId) throws IOException {
			final int t = viewId.getTimePointId();
			final int s = viewId.getViewSetupId();
			writer.write(Integer.toString(t));
			writer.write(" ");
			writer.write(Integer.toString(s));
			writer.write(" ");
			writer.newLine();
		}

		void writeMatches(final int coeff1, final int coeff2, final int numVoxels, final Collection<PointMatch> matches) throws IOException {
			writer.write(String.format("%d %d %d ", coeff1, coeff2, numVoxels));
			for (final PointMatch match : matches) {
				final double l1 = match.getP1().getL()[0];
				final double l2 = match.getP2().getL()[0];
				writer.write(Double.toString(l1));
				writer.write(" ");
				writer.write(Double.toString(l2));
				writer.write(" ");
			}
			writer.newLine();
		}

		@Override
		public void close() throws IOException {
			writer.close();
		}
	}

	static class CoefficientMatch {

		final int coeff1;
		final int coeff2;
		final int numVoxels;
		final Collection<PointMatch> matches;

		CoefficientMatch(final int coeff1, final int coeff2, final int numVoxels, final Collection<PointMatch> matches) {
			this.coeff1 = coeff1;
			this.coeff2 = coeff2;
			this.numVoxels = numVoxels;
			this.matches = matches;
		}
	}

	static class Reader implements Closeable {

		private final BufferedReader reader;

		Reader(final String filePath) throws IOException {
			reader = Files.newBufferedReader(Paths.get(filePath));
		}

		ViewId readViewId() throws IOException {
			final String line = reader.readLine();
			if (line == null)
				return null;

			final String[] tokens = line.split("\\s+");
			final int t = Integer.parseInt(tokens[0]);
			final int s = Integer.parseInt(tokens[1]);
			return new ViewId(t, s);
		}

		CoefficientMatch readMatches() throws IOException {
			final String line = reader.readLine();
			if (line == null)
				return null;

			final String[] tokens = line.split("\\s+");
			final int coeff1 = Integer.parseInt(tokens[0]);
			final int coeff2 = Integer.parseInt(tokens[1]);
			final int numVoxels = Integer.parseInt(tokens[2]);

			final List<PointMatch> matches = new ArrayList<>();
			for (int i = 3; i < tokens.length; i += 2) {
				final double l1 = Double.parseDouble(tokens[i]);
				final double l2 = Double.parseDouble(tokens[i + 1]);
				matches.add(new PointMatch1D(new Point1D(l1), new Point1D(l2)));
			}
			return new CoefficientMatch(coeff1, coeff2, numVoxels, matches);
		}

		@Override
		public void close() throws IOException {
			reader.close();
		}
	}
}
