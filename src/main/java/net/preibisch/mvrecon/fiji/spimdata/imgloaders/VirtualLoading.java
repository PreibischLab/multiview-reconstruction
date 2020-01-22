package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformView;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.TransformWeight;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;

public class VirtualLoading {
	public static <S extends AbstractSequenceDescription<?, ? extends BasicViewDescription<? extends BasicViewSetup>, ?>> List<RandomAccessibleInterval<FloatType>> openVirtuallyFused(
			S sd, ViewRegistrations vrs, Collection<? extends Collection<ViewId>> views, Interval boundingBox,
			double[] downsamplingFactors) {
		final BasicImgLoader imgLoader = sd.getImgLoader();

		final List<RandomAccessibleInterval<FloatType>> openImgs = new ArrayList<>();
		final Interval bbSc = TransformVirtual.scaleBoundingBox(new FinalInterval(boundingBox),
				inverse(downsamplingFactors));

		final long[] dim = new long[bbSc.numDimensions()];
		bbSc.dimensions(dim);

		for (Collection<ViewId> viewGroup : views) {
			final ArrayList<RandomAccessibleInterval<FloatType>> images = new ArrayList<>();
			final ArrayList<RandomAccessibleInterval<FloatType>> weights = new ArrayList<>();

			for (final ViewId viewId : viewGroup) {
				final ViewRegistration vr = vrs.getViewRegistration(viewId);
				vr.updateModel();
				AffineTransform3D model = vr.getModel();

				final float[] blending = Util.getArrayFromValue(FusionTools.defaultBlendingRange, 3);
				final float[] border = Util.getArrayFromValue(FusionTools.defaultBlendingBorder, 3);

				model = model.copy();
				TransformVirtual.scaleTransform(model, inverse(downsamplingFactors));

				final RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled(imgLoader, viewId, model);

				System.out.println(model.inverse());

				FusionTools.adjustBlending(sd.getViewDescriptions().get(viewId), blending, border, model);

				images.add(TransformView.transformView(inputImg, model, bbSc, 0, 1));
				weights.add(TransformWeight.transformBlending(inputImg, border, blending, model, bbSc));
			}

			openImgs.add(new FusedRandomAccessibleInterval(new FinalInterval(dim), images, weights));

		}

		return openImgs;

	}

	public static double[] inverse(double[] in) {
		final double[] res = new double[in.length];
		for (int i = 0; i < in.length; i++)
			res[i] = 1 / in[i];
		return res;
	}
}
