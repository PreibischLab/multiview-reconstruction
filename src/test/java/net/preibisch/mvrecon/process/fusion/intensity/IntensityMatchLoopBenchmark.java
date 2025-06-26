package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealInterval;
import net.imglib2.util.BenchmarkHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import static net.preibisch.mvrecon.process.fusion.intensity.IntensityCorrection.getPotentialBleachers;

public class IntensityMatchLoopBenchmark {

	private static final double minIntensityThreshold = 5.0;
	private static final double maxIntensityThreshold = 250.0;
	private static final int minNumCandidates = 1000;
	private static final int iterations = 1000;
	private static final double maxEpsilon = 0.1 * 255;
	private static final double minInlierRatio = 0.1;
	private static final int minNumInliers = 10;
	private static final double maxTrust = 3.0;

	public static void main(String[] args) throws URISyntaxException, SpimDataException, IOException {

		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.WARN);


		final URI xml = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(xml);
		final SequenceDescription seq = spimData.getSequenceDescription();

		final int timepointId = 0; // TODO: make argument

		final List<ViewId> views = seq.getViewSetupsOrdered().stream()
				.map(setup -> new ViewId(timepointId, setup.getId()))
				.collect(Collectors.toList());

		// maps every ViewId to the bounds of the view in global coordinates
		final Map<ViewId, RealInterval> viewBounds = new HashMap<>();
		for (final ViewId viewId : views) {
			final RealInterval bounds = IntensityCorrection.getBounds(spimData, viewId);
			viewBounds.put(viewId, bounds);
		}

		// maps every ViewId to the ViewIds of potential "bleachers" (other views with overlapping bounds)
		final Map<ViewId, List<ViewId>> bleachers = getPotentialBleachers(views, viewBounds);
		for (final ViewId view : views) {
			System.out.println(view.getViewSetupId() + " : " +
					Arrays.toString(bleachers.get(view).stream()
							.mapToInt(ViewId::getViewSetupId)
							.toArray()));
		}

		BenchmarkHelper.benchmarkAndPrint(10, false, () -> {
			final double renderScale = 0.25;
			int[] coefficientsSize = {8, 8, 8};
			final IntensityMatcher matcher = new IntensityMatcher(spimData, renderScale, coefficientsSize);
			for (int i = 0; i < views.size(); ++i) {
				final ViewId view1 = views.get(i);
				final List<ViewId> view1Bleachers = bleachers.get(view1);
				for (int j = i + 1; j < views.size(); ++j) {
					final ViewId view2 = views.get(j);
					final List<ViewId> view2Bleachers = bleachers.get(view2);
					matcher.match(view1, view1Bleachers, view2, view2Bleachers,
							minIntensityThreshold, maxIntensityThreshold, minNumCandidates, iterations,
							maxEpsilon, minInlierRatio, minNumInliers, maxTrust);
				}
			}
		});
	}
}
