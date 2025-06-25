package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealInterval;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import static net.imglib2.util.Intervals.intersect;
import static net.imglib2.util.Intervals.isEmpty;

public class BleachingOverlapPlayground
{
	public static void main(String[] args) throws URISyntaxException, SpimDataException, IOException {

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
	}

	/**
	 * Given a list of {@code ViewIds} in the order they were acquired (such
	 * that views later in the list have been potentially bleached by earlier
	 * views), finds for each {@code ViewId} the potential "bleachers" (earlier
	 * views with overlapping bounds).
	 *
	 * @param views list of all {@code ViewIds} in the order they were acquired
	 * @param viewBounds maps {@code ViewId} to bounding box in world coordinates
	 * @return maps every ViewId to the ViewIds of potential bleachers
	 */
	public static Map<ViewId, List<ViewId>> getPotentialBleachers(
			final List<ViewId> views,
			final Map<ViewId, RealInterval> viewBounds
	) {
		final Map<ViewId, List<ViewId>> viewBleachers = new HashMap<>();
		for (int i = 0; i < views.size(); ++i) {
			final ViewId view0 = views.get(i);
			final RealInterval bounds0 = viewBounds.get(view0);
			final List<ViewId> bleachers = new ArrayList<>();
			viewBleachers.put(view0, bleachers);
			for (int j = 0; j < i; ++j) {
				final ViewId view1 = views.get(j);
				final RealInterval bounds1 = viewBounds.get(view1);
				if (!isEmpty(intersect(bounds0, bounds1))) {
					bleachers.add(view1);
				}
			}
		}
		return viewBleachers;
	}
}
