package net.preibisch.mvrecon.process.fusion.intensity;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class IntensityPlayground {

	static RealInterval scale(RealInterval interval, double scale) {
		final AffineTransform3D t = new AffineTransform3D();
		t.scale(scale);
		return t.estimateBounds(interval);
	}

	public static void main(String[] args) throws URISyntaxException, SpimDataException {
		final URI xml = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(xml);
		final SequenceDescription seq = spimData.getSequenceDescription();


		final ViewId id0 = new ViewId(0, 0);
		final ViewId id1 = new ViewId(0, 1);


		final double renderScale = 0.25;
		final IntensityMatcher matcher = new IntensityMatcher(spimData, renderScale, new int[] {8, 8, 8});
		matcher.matchAndConnect(id0, id1);


		final TileInfo tile0 = matcher.getTileInfo(id0);
		final TileInfo tile1 = matcher.getTileInfo(id1);

		final RandomAccessible<IntType> tempCoefficientsMask1 = matcher.scaleTileCoefficients(tile0);
		final RandomAccessible<IntType> tempCoefficientsMask2 = matcher.scaleTileCoefficients(tile1);
		final RandomAccessible<UnsignedByteType> tempCoefficientMask1 = matcher.scaleTileCoefficient(tile0, 0,0,0);
		final RandomAccessible<UnsignedByteType> tempCoefficientMask2 = matcher.scaleTileCoefficient(tile1, 7,0,0);


		final int l = matcher.bestMipmapLevel(tile0);
		final RandomAccessible<UnsignedShortType> rendered1 = matcher.scaleTileImage(tile0, l);
		final RandomAccessible<UnsignedShortType> rendered2 = matcher.scaleTileImage(tile1, l);


		// show in BDV
		final BdvHandle bdv = BdvFunctions.show();
		final List< BdvStackSource< ? > > spimdataSources = BdvFunctions.show( spimData, Bdv.options().addTo( bdv ) );
		spimdataSources.get(0).setDisplayRange(0, 1000);
		spimdataSources.get(1).setDisplayRange(0, 1000);

		final RealInterval overlap = IntensityMatcher.getOverlap(tile0, tile1);
		final Interval renderBounds = Intervals.smallestContainingInterval(scale(overlap, renderScale));

		BdvFunctions.show(tempCoefficientsMask1, renderBounds, "mask 1",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 512);
		BdvFunctions.show(tempCoefficientsMask2, renderBounds, "mask 2",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 512);


		BdvFunctions.show(tempCoefficientMask1, renderBounds, "mask 1 (0)",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 1);
		BdvFunctions.show(tempCoefficientMask2, renderBounds, "mask 2 (7)",
				Bdv.options().addTo(bdv).sourceTransform(4,4,4)).setDisplayRange(0, 1);

		BdvFunctions.show(rendered1.view().interval(renderBounds), "rendered 1",
				Bdv.options().addTo(bdv).sourceTransform(4, 4, 4)).setDisplayRange(0, 1000);
		BdvFunctions.show(rendered2.view().interval(renderBounds), "rendered 2",
				Bdv.options().addTo(bdv).sourceTransform(4, 4, 4)).setDisplayRange(0, 1000);



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
