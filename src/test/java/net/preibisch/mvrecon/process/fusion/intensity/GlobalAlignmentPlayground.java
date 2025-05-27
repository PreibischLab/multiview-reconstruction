package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import util.URITools;

public class GlobalAlignmentPlayground {

	public static void main(String[] args) throws URISyntaxException, SpimDataException {

		final URI xml = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(xml);
		final SequenceDescription seq = spimData.getSequenceDescription();

		final int timepointId = 0; // TODO: make argument

		final ViewId[] views = seq.getViewSetupsOrdered().stream()
				.map(setup -> new ViewId(timepointId, setup.getId()))
				.toArray(ViewId[]::new);

		final double renderScale = 0.25;
		final String outputDirectory = "/Users/pietzsch/Desktop/matches/";
		final IntensityMatcher matcher = new IntensityMatcher(spimData, renderScale, new int[] {8, 8, 8}, outputDirectory);
		final boolean writeMatches = false;
		if (writeMatches) {
			for (int i = 0; i < views.length; ++i) {
				for (int j = i + 1; j < views.length; ++j) {
					System.out.println("matching view " + views[i] + " and " + views[j]);
					matcher.match(views[i], views[j]);
				}
			}
		} else {
			for (int i = 0; i < views.length; ++i) {
				for (int j = i + 1; j < views.length; ++j) {
					System.out.println("matching view " + views[i] + " and " + views[j]);
					matcher.readFromFile(views[i], views[j], outputDirectory);
				}
			}
		}

		final IntensityCorrection solver = new IntensityCorrection();
		solver.solveForGlobalCoefficients(matcher.getIntensityTiles(), 1000);


		final URI uri = new File( "/Users/pietzsch/Desktop/intensity.n5" ).toURI();
		try ( final N5Writer n5Writer = URITools.instantiateN5Writer( StorageFormat.N5, uri ) )
		{
			IntensityCorrection.writeCoefficients(
					n5Writer,
					"",
					"coefficients",
					matcher.getIntensityTiles()
			);
		}

		// TODO: iterate IntensityTiles
		//       for each IntensityTile iterate Coefficient Tiles
		//       extract Coefficient instance (scale models by 255!?)

	}
}
