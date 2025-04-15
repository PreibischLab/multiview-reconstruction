package net.preibisch.mvrecon.process.fusion.intensity;

import java.util.Arrays;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;

class TileInfo {

	final int[] numCoeffs;
	final Dimensions dimensions;
	final AffineTransform3D model;

	/**
	 * coefficient coordinate to coefficient region center in image coordinates
	 */
	final AffineTransform3D coeffCenterTransform;

	/**
	 * coefficient coordinate to min bound of coefficient region center in image coordinates
	 */
	final AffineTransform3D coeffBoundsTransform;

	TileInfo(final int numCoefficients, final SpimData spimData, final ViewId view) {
		this.numCoeffs = new int[] {numCoefficients, numCoefficients, numCoefficients};

		final ImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
		final SetupImgLoader<?> setupImgLoader = imgLoader.getSetupImgLoader(view.getViewSetupId());
		this.dimensions = setupImgLoader.getImageSize(view.getTimePointId());
		this.model = spimData.getViewRegistrations().getViewRegistration(view).getModel();

		final double[] scale = new double[numCoeffs.length];
		Arrays.setAll(scale, d -> (double) dimensions.dimension(d) / numCoeffs[d]);

		coeffCenterTransform = new AffineTransform3D();
		coeffCenterTransform.set(
				scale[0], 0, 0, scale[0] * 0.5,
				0, scale[1], 0, scale[1] * 0.5,
				0, 0, scale[2], scale[2] * 0.5
		);

		coeffBoundsTransform = new AffineTransform3D();
		coeffBoundsTransform.set(
				scale[0], 0, 0, -0.5,
				0, scale[1], 0, -0.5,
				0, 0, scale[2], -0.5
		);
	}
}
