package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import mpicbg.spim.data.sequence.ViewId;
import org.janelia.saalfeldlab.n5.KeyValueAccess;
import org.janelia.saalfeldlab.n5.N5Exception;
import util.URITools;

public class ViewPairCoefficientMatchesIO {

	private final URI uri;
	private final KeyValueAccess kva;

	/**
	 * Reduced matches are read/written from/to text files in this directory
	 */
//	private final String directory;

//	public ViewPairCoefficientMatchesIO(final String directory) {
//		this.directory = directory;
//	}

	public ViewPairCoefficientMatchesIO(final URI uri) {
		this.uri = uri;
		kva = URITools.getKeyValueAccess( uri );
	}

	// TODO remove?
	public void write(
			final ViewId p1,
			final ViewId p2,
			final List<IntensityMatcher.CoefficientMatch> coefficientMatches
	) throws IOException {
		final URI fn = getURI(p1, p2);
		try (
				final OutputStream os = URITools.openFileWriteCloudStream(kva, fn);
				final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
				final IntensityMatchesIO.Writer output = new IntensityMatchesIO.Writer(bw);) {
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
		final URI fn = getURI(p1, p2);
		return readFromFile(fn);
	}

	private ViewPairCoefficientMatches readFromFile(final URI fn)
			throws IOException {
		try (
				final InputStream is = URITools.openFileReadCloudStream(kva, fn);
				final BufferedReader br = new BufferedReader(new InputStreamReader(is));
				final IntensityMatchesIO.Reader input = new IntensityMatchesIO.Reader(br);) {
			final ViewId p1 = input.readViewId();
			final ViewId p2 = input.readViewId();
			List<IntensityMatcher.CoefficientMatch> coefficientMatches = new ArrayList<>();
			IntensityMatcher.CoefficientMatch match;
			while ((match = input.readMatches()) != null) {
				coefficientMatches.add(match);
			}
			return new ViewPairCoefficientMatches(p1, p2, coefficientMatches);
		} catch (N5Exception.N5NoSuchKeyException e) {
			return null;
		}
	}

	private URI getURI(final ViewId p1, final ViewId p2) {
		return uri.resolve(String.format("t%d_s%d--t%d_s%d.txt",
				p1.getTimePointId(), p1.getViewSetupId(),
				p2.getTimePointId(), p2.getViewSetupId()));
	}
}
