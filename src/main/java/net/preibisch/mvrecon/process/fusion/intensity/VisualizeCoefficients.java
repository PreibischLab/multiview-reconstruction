package net.preibisch.mvrecon.process.fusion.intensity;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOverlay;
import bdv.util.BdvStackSource;
import java.awt.Color;
import java.awt.Graphics2D;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class VisualizeCoefficients {

	static class CoefficientsOverlay extends BdvOverlay {

		private final TileInfo[] tileInfos;
		private final Color[] colors;

		public CoefficientsOverlay(final TileInfo... tileInfos) {
			this.tileInfos = tileInfos;

			colors = new Color[tileInfos.length];
			final DistinctColorStream cs = new DistinctColorStream();
			Arrays.setAll(colors, i -> cs.getNextColor());
		}

		@Override
		protected void draw(final Graphics2D graphics) {
			final AffineTransform3D transform = new AffineTransform3D();

			for (int i = 0; i < tileInfos.length; i++) {
				final TileInfo tileInfo = tileInfos[i];
				final Color color = colors[i];

				info.getViewerTransform(transform);
				transform.concatenate(tileInfo.model);
				transform.concatenate(tileInfo.coeffCenterTransform);

				final double[] lPos = new double[3];
				final double[] gPos = new double[3];
				for (int lx = 0; lx < tileInfo.numCoeffs[0]; ++lx) {
					lPos[0] = lx;
					for (int ly = 0; ly < tileInfo.numCoeffs[1]; ++ly) {
						lPos[1] = ly;
						for (int lz = 0; lz < tileInfo.numCoeffs[2]; ++lz) {
							lPos[2] = lz;
							transform.apply(lPos, gPos);
							final double size = getPointSize(gPos);
							final int x = (int) (gPos[0] - 0.5 * size);
							final int y = (int) (gPos[1] - 0.5 * size);
							final int w = (int) size;
							graphics.setColor(getColor(gPos, color));
							graphics.fillOval(x, y, w, w);

						}
					}
				}
			}
		}

		private Color getColor(final double[] gPos, final Color col) {
			int alpha = 255 - (int) Math.round(Math.abs(gPos[2]));
			if (alpha < 64)
				alpha = 64;
			return new Color(col.getRed(), col.getGreen(), col.getBlue(), alpha);
		}

		private double getPointSize(final double[] gPos) {
			final double maxdist = 100;
			final double maxsize = 10;
			final double minsize = 2;
			return Math.max(maxsize * (maxdist - Math.abs(gPos[2])) / maxdist, minsize);
		}
	}

	public static void main(String[] args) throws Exception {
		final URI xml = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(xml);

		final List<BdvStackSource<?>> stackSources = BdvFunctions.show(spimData, Bdv.options());
		final Bdv bdv = stackSources.get(0);

		final TileInfo[] tiles = spimData.getSequenceDescription()
				.getViewSetupsOrdered()
				.stream()
				.map(v -> new ViewId(0, v.getId()))
				.map(viewId -> new TileInfo(8, spimData, viewId))
				.toArray(TileInfo[]::new);
		final BdvOverlay overlay = new CoefficientsOverlay(Arrays.copyOf(tiles, 3));
		BdvFunctions.showOverlay(overlay, "coefficients", Bdv.options().addTo(bdv));
	}

}
