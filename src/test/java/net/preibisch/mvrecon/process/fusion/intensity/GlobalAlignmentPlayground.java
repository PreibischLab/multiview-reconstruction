package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.Arrays;
import java.util.List;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import util.URITools;

public class GlobalAlignmentPlayground {

	public static void main(String[] args) throws URISyntaxException, SpimDataException, IOException {

		final URI xml = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(xml);
		final SequenceDescription seq = spimData.getSequenceDescription();

		final int timepointId = 0; // TODO: make argument

		final ViewId[] views = seq.getViewSetupsOrdered().stream()
				.map(setup -> new ViewId(timepointId, setup.getId()))
				.toArray(ViewId[]::new);

		final double renderScale = 0.25;
		final String outputDirectory = "file:/Users/pietzsch/Desktop/matches_uri/";
		final ViewPairCoefficientMatchesIO matchesIO = new ViewPairCoefficientMatchesIO(URI.create(outputDirectory));
		int[] coefficientsSize = {8, 8, 8};
		matchesIO.writeCoefficientsSize(coefficientsSize);
		final IntensityMatcher matcher = new IntensityMatcher(spimData, renderScale, coefficientsSize);
		final IntensitySolver intensitySolver = new IntensitySolver(coefficientsSize);
		final boolean writeMatches = false;
		if (writeMatches) {
			for (int i = 0; i < views.length; ++i) {
				for (int j = i + 1; j < views.length; ++j) {
					System.out.println("matching view " + views[i] + " and " + views[j]);
					final List<IntensityMatcher.CoefficientMatch> coefficientMatches = matcher.match(views[i], views[j]);
					intensitySolver.connect(views[i], views[j], coefficientMatches);
					matchesIO.write(views[i], views[j], coefficientMatches);
				}
			}
		} else {
			final int[] coefficientsSizeR = matchesIO.readCoefficientsSize();
			System.out.println("coefficientsSizeR = " + Arrays.toString(coefficientsSizeR));
			for (int i = 0; i < views.length; ++i) {
				for (int j = i + 1; j < views.length; ++j) {
					System.out.println("matching view " + views[i] + " and " + views[j]);
					try {
						final ViewPairCoefficientMatches matches = matchesIO.read(views[i], views[j]);
						if (matches != null) {
							intensitySolver.connect(matches);
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}

		intensitySolver.solve(1000);

		final URI uri = new File( "/Users/pietzsch/Desktop/intensity.n5" ).toURI();
		try ( final N5Writer n5Writer = URITools.instantiateN5Writer( StorageFormat.N5, uri ) )
		{
			IntensityCorrection.writeCoefficients(
					n5Writer,
					"",
					"coefficients",
					intensitySolver.getIntensityTiles()
			);
		}

		// TODO: iterate IntensityTiles
		//       for each IntensityTile iterate Coefficient Tiles
		//       extract Coefficient instance (scale models by 255!?)

	}
}
