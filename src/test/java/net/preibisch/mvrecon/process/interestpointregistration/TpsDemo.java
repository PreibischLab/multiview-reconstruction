package net.preibisch.mvrecon.process.interestpointregistration;


import ij.IJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.util.Intervals;
import net.imglib2.view.fluent.RandomAccessibleIntervalView.Extension;
import net.imglib2.view.fluent.RandomAccessibleView.Interpolation;
import net.imglib2.view.fluent.RealRandomAccessibleView;

public class TpsDemo {

	public static void main(String[] args) {

		// Img img = ImageJFunctions.wrap(
		// IJ.openImage("http://imagej.net/images/boats.gif"));
		Img img = ImageJFunctions.wrapByte(IJ.openImage("/home/john/tmp/boats.tif"));
		System.out.println(Intervals.toString(img));

		double sx = img.dimension(0);
		double sy = img.dimension(1);
		double[][] points = new double[][]{
			{sx / 2, 0, sx, 0, sx}, // x
			{sy / 2, 0, 0, sy, sy}  // y
		};

		double[][] displacements = new double[][]{
			{50, -50, -50, -50, -50}, 		// x displacement
			{50, -100, -100, -100, -100} 	// y displacement
		};

		ThinplateSplineTransform transform = buildTransform(points, displacements);

		RealRandomAccessibleView interp = img.view().extend(Extension.zero()).interpolate(Interpolation.nLinear());
		RandomAccessibleInterval tformedImg = new RealTransformRealRandomAccessible(interp, transform).realView().raster().interval(img);
		ImageJFunctions.show(tformedImg);
	}

	public static ThinplateSplineTransform buildTransform(
			double[][] points2xN,
			double[][] displacements2xN) {

		return new ThinplateSplineTransform(
				// build corresponding points from displacements
				// if you have points already, just use them
				pointsFromDisplacements(points2xN, displacements2xN),
				points2xN);
	}

	/**
	 * Takes a set of points and displacements and outputs a set of
	 * corresponding points
	 */
	public static double[][] pointsFromDisplacements(
			double[][] points2xN,
			double[][] displacements2xN) {

		int nd = points2xN.length;
		int N = points2xN[0].length;

		double[][] pts = new double[nd][N];
		for (int d = 0; d < nd; d++) {
			for (int i = 0; i < N; i++) {
				pts[d][i] = points2xN[d][i] + displacements2xN[d][i];
			}
		}
		return pts;
	}

}