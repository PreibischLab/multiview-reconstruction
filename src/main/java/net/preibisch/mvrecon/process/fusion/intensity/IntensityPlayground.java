package net.preibisch.mvrecon.process.fusion.intensity;

import bdv.BigDataViewer;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import java.net.URI;
import java.net.URISyntaxException;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class IntensityPlayground {

	public static void main(String[] args) throws URISyntaxException, SpimDataException {
		final URI xml = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(xml);
		final SequenceDescription seq = spimData.getSequenceDescription();


		final ViewId id0 = new ViewId(0, 0);
		final ViewId id1 = new ViewId(0, 1);


		final IntensityMatcher matcher = new IntensityMatcher(spimData, 1. / 4, 8);
		matcher.match(id0, id1);

		// show in BDV
		final BdvHandle bdv = BdvFunctions.show();
		final SourceAndConverter<?> soc0 = BigDataViewer.createSetupSourceNumericType(spimData, 0, "setup 0", bdv.getResourceManager());
		final SourceAndConverter<?> soc1 = BigDataViewer.createSetupSourceNumericType(spimData, 1, "setup 1", bdv.getResourceManager());
		BdvFunctions.show(soc0, Bdv.options().addTo(bdv)).setDisplayRange(0, 1000);
		BdvFunctions.show(soc1, Bdv.options().addTo(bdv)).setDisplayRange(0, 1000);

		final RandomAccessibleInterval<?> interval = matcher.rendered1;
		BdvFunctions.show(matcher.tempCoefficientsMask1, interval, "mask 1",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 512);
		BdvFunctions.show(matcher.tempCoefficientsMask2, interval, "mask 2",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 512);

		BdvFunctions.show(matcher.tempCoefficientMask1, interval, "mask 1 (0)",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 1);
		BdvFunctions.show(matcher.tempCoefficientMask2, interval, "mask 2 (7)",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 1);

		BdvFunctions.show(matcher.rendered1, "rendered 1",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 1000);
		BdvFunctions.show(matcher.rendered2, "rendered 2",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 1000);



	/*

		final double[] factors = new double[ 3 ];
		final AffineTransform3D model = new AffineTransform3D();
		model.scale(0.1);
		final Pair<RandomAccessibleInterval, AffineTransform3D> pair = DownsampleTools.openDownsampled2(seq.getImgLoader(), id0, model, factors);

		System.out.println("pair.getB() = " + pair.getB());
		System.out.println("factors = " + Arrays.toString(factors));

//		final SequenceDescription seq = spimData.getSequenceDescription();
//		final ViewerSetupImgLoader<?, ?> sil = Cast.unchecked( seq.getImgLoader().getSetupImgLoader(0) );

		// --- figuring stuff out ------------

		System.out.println("spimData = " + spimData);
		for (final ViewSetup viewSetup : seq.getViewSetupsOrdered()) {
			System.out.println("viewSetup = " + viewSetup);
		}

		final ViewerSetupImgLoader<?, ?> sil = Cast.unchecked( seq.getImgLoader().getSetupImgLoader(0) );
		System.out.println("sil.getImageType().getClass() = " + sil.getImageType().getClass());

		sil.getMipmapResolutions();
		System.out.println("Arrays.deepToString(sil.getMipmapResolutions()) = " + Arrays.deepToString(sil.getMipmapResolutions()));
*/
	}
}
