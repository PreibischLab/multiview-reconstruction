package net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;

import fiji.tool.SliceObserver;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.Opener;
import ij.process.ImageProcessor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.converter.Converters;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;

public class InteractiveRadialSymmetry
{
	// TODO: Pass as the parameter (?)
	final int sensitivity = 4;//RadialSymParams.defaultSensitivity;

	// Frames that are potentially open
	DoGWindow dogWindow;

	// TODO: Pass them or their values
	SliceObserver sliceObserver;
	ROIListener roiListener;

	final ImagePlus imagePlus;
	//final boolean normalize; // do we normalize intensities?
	//final double min, max; // intensity of the imageplus

	final RandomAccessibleInterval< FloatType > img;

	final long[] dim;
	//final int type;
	Rectangle rectangle;

	ArrayList<RefinedPeak<Point>> peaksMin = null, peaksMax = null;

	// TODO: always process only this part of the initial image READ ONLY
	RandomAccessibleInterval<FloatType> imgTmp;
	Interval extendedRoi;

	boolean isComputing = false;
	boolean isStarted = false;

	public static enum ValueChange {
		SIGMA, THRESHOLD, SLICE, ROI, ALL, MINMAX
	}
	
	// stores all the parameters 
	final IDoGParams params;
	
	// min/max values for GUI
	public static final int supportRadiusMin = 1;
	public static final int supportRadiusMax = 25;
	public static final float inlierRatioMin = (float) (0.0 / 100.0); // 0%
	public static final float inlierRatioMax = 1; // 100%
	public static final float maxErrorMin = 0.0001f;
	public static final float maxErrorMax = 10.00f;
	
	// min/max value
	final float bsInlierRatioMin = (float) (0.0 / 100.0); // 0%
	final float bsInlierRatioMax = 1; // 100%
	final float bsMaxErrorMin = 0.0001f;
	final float bsMaxErrorMax = 10.00f;
	
	// min/max value
	public static final float sigmaMin = 0.5f;
	public static final float sigmaMax = 10f;
	public static final float thresholdMin = 0.00001f;
	public static final float thresholdMax = 1f;
	
	final int scrollbarSize = 1000;
	// ----------------------------------------
	
	boolean isFinished = false;
	boolean wasCanceled = false;	

	public boolean isFinished() {
		return isFinished;
	}

	public boolean wasCanceled() {
		return wasCanceled;
	}

	public InteractiveRadialSymmetry( final ImagePlus imp, final IDoGParams params )
	{
		this( imp, params, Double.NaN, Double.NaN );
	}
	
	/**
	 * Triggers the interactive radial symmetry plugin
	 * Single-channel imageplus, 2d or 3d or 4d
	 * 
	 * @param imp - intial image
	 * @param params - parameters for the computation of the radial symmetry
	 * @param min - min intensity of the image
	 * @param max - max intensity of the image
	 */
	@SuppressWarnings("unchecked")
	public InteractiveRadialSymmetry( final ImagePlus imp, final IDoGParams params, final double min, final double max )
	{
		this.imagePlus = imp;

		if ( Double.isNaN( min ) || Double.isNaN( max ) )
		{
			throw new RuntimeException( "min/max not set for interactive DoG." );
			//this.img = Converters.convert( (RandomAccessibleInterval<RealType>)(Object)ImagePlusImgs.from( imp ), (i,o) -> o.set(i.getRealFloat()), new FloatType() );
		}
		else
		{
			final double range = max - min;

			this.img = Converters.convert(
					(RandomAccessibleInterval<RealType<?>>)(Object)ImagePlusImgs.from( imp ),
					(i,o) ->
					{
						o.set( (float)( ( i.getRealFloat() - min ) / range ) );
					},
					new FloatType() );
		}

		this.params = params;
		this.dim = new long[]{ imp.getWidth(), imp.getHeight() };

		final Roi roi = imagePlus.getRoi();

		if ( roi != null && roi.getType() == Roi.RECTANGLE  )
		{
			rectangle = roi.getBounds();
		}
		else
		{
			// initial rectangle
			rectangle = new Rectangle(
					imagePlus.getWidth() / 4,
					imagePlus.getHeight() / 4,
					Math.min( 100, imagePlus.getWidth() / 2 ),
					Math.min( 100, imagePlus.getHeight() / 2) );

			imagePlus.setRoi( rectangle );
		}

		initInteractiveKit();
	}
	
	
	/**
	 *	Initialize the image kit - DoG and RANSAC windows to adjust the parameters
	 * */
	protected void initInteractiveKit(){
		// show the interactive dog kit
		this.dogWindow = new DoGWindow( this );
		this.dogWindow.getFrame().setVisible( true );

		// add listener to the imageplus slice slider
		sliceObserver = new SliceObserver(imagePlus, new ImagePlusListener( this ));
		// compute first version
		updatePreview(ValueChange.ALL);
		isStarted = true;
		// check whenever roi is modified to update accordingly
		roiListener = new ROIListener( this, imagePlus );
		imagePlus.getCanvas().addMouseListener( roiListener );
	}

	// TODO: fix the check: "==" must not be used with floats
	protected boolean isRoiChanged(final ValueChange change, final Rectangle rect, boolean roiChanged){
		boolean res = false;
		res = (roiChanged || extendedRoi == null || change == ValueChange.SLICE ||rect.getMinX() != rectangle.getMinX()
				|| rect.getMaxX() != rectangle.getMaxX() || rect.getMinY() != rectangle.getMinY()
				|| rect.getMaxY() != rectangle.getMaxY());
		return res;
	}

	/**
	 * Updates the Preview with the current parameters (sigma, threshold, roi, slice number + RANSAC parameters)
	 * @param change - what did change
	 */
	protected void updatePreview(final ValueChange change) {
		// set up roi 
		boolean roiChanged = false;
		Roi roi = imagePlus.getRoi();

		if ( roi == null || roi.getType() != Roi.RECTANGLE )
		{
			imagePlus.setRoi(rectangle);
			roi = imagePlus.getRoi();
			roiChanged = true;
		}

		// Do I need this one or it is just the copy of the same thing?
		// sourceRectangle or rectangle
		final Rectangle roiBounds = roi.getBounds(); 

		// change the img2 size if the roi or the support radius size was changed
		if ( isRoiChanged(change, roiBounds, roiChanged) || change == ValueChange.SIGMA )
		{
			rectangle = roiBounds;

			// make sure the size is not 0 (is possible in ImageJ when making the Rectangle, not when changing it ... yeah)
			rectangle.width = Math.max( 1, rectangle.width );
			rectangle.height = Math.max( 1, rectangle.height );

			// a 2d or 3d view where we'll run DoG on
			//RandomAccessibleInterval< FloatType > imgTmp;
			long[] min, max;

			if ( imagePlus.getNSlices() > 1 ) { // 3d, 3d+t case

				if ( imagePlus.getNFrames() > 1 )
					imgTmp = Views.hyperSlice( img, 3, imagePlus.getT() - 1 );
				else
					imgTmp = img;

				// 3d case

				// 'channel', 'slice' and 'frame' are one-based indexes
				final int currentSlice = imagePlus.getZ() - 1;

				/*
				final int extZ = 
						Gauss3.halfkernelsizes(
								new double[] {
										HelperFunctions.computeSigma2( params.getSigmaDoG(), sensitivity ) *
										( params.useAnisotropyForDoG ? params.anisotropyCoefficient : 1.0 ) } )[ 0 ];
				*/
				// we need only one plane (+-1) in Z since we anyways use the entire image for convolution
				min = new long []{
						rectangle.x,
						rectangle.y,
						Math.max( imgTmp.min( 2 ), currentSlice - 1 ) };
				max = new long []{
						rectangle.width + rectangle.x - 1,
						rectangle.height + rectangle.y - 1,
						Math.min( imgTmp.max( 2 ), currentSlice + 1 ) };
			}
			else { // 2d or 2d+t case

				if ( imagePlus.getNFrames() > 1 )
					imgTmp = Views.hyperSlice( img, 2, imagePlus.getT() - 1 );
				else
					imgTmp = img;

				// 2d case

				min = new long []{rectangle.x, rectangle.y};
				max = new long []{rectangle.width + rectangle.x - 1, rectangle.height + rectangle.y - 1};
			}

			extendedRoi = new FinalInterval(min, max);//Views.interval( Views.extendMirrorSingle( imgTmp ), min, max);

			roiChanged = true;
		}

		// only recalculate DOG & gradient image if: sigma, roi (also through support region), slider
		if (roiChanged || peaksMin == null || peaksMax == null || change == ValueChange.SIGMA || change == ValueChange.SLICE || change == ValueChange.MINMAX || change == ValueChange.ALL )
		{
			dogDetection( Views.extendMirrorSingle( imgTmp ), extendedRoi );
		}

		final double radius = ( ( params.sigma + HelperFunctions.computeSigma2( params.sigma, sensitivity ) ) / 2.0 );
		final ArrayList< RefinedPeak< Point > > filteredPeaksMax = HelperFunctions.filterPeaks( peaksMax, rectangle, params.threshold );
		final ArrayList< RefinedPeak< Point > > filteredPeaksMin = HelperFunctions.filterPeaks( peaksMin, rectangle, params.threshold );

		HelperFunctions.drawRealLocalizable( filteredPeaksMax, imagePlus, radius, Color.RED, true );
		HelperFunctions.drawRealLocalizable( filteredPeaksMin, imagePlus, radius, Color.GREEN, false );

		isComputing = false;
	}

	protected void dogDetection( final RandomAccessibleInterval <FloatType> image )
	{
		dogDetection( image, image );
	}

	protected void dogDetection( final RandomAccessible<FloatType> image, final Interval interval )
	{
		final double sigma2 = HelperFunctions.computeSigma2( params.sigma, sensitivity );

		double[] calibration = new double[ image.numDimensions() ];
		calibration[ 0 ] = 1.0;
		calibration[ 1 ] = 1.0;
		if ( calibration.length == 3 )
			calibration[ 2 ] = 1.0;

		this.peaksMin = new ArrayList<>();
		this.peaksMax = new ArrayList<>();

		if ( params.findMaxima )
		{
			final DoGDetection<FloatType> dog2 =
					new DoGDetection<>(image, interval, calibration, params.sigma, sigma2 , DoGDetection.ExtremaType.MINIMA, InteractiveRadialSymmetry.thresholdMin, false);
	
			ArrayList<Point> simplePeaks = dog2.getPeaks();
			RandomAccess<?> dog = (Views.extendBorder(dog2.getDogImage())).randomAccess();
	
			for ( final Point p : simplePeaks )
			{
				dog.setPosition( p );
				peaksMax.add( new RefinedPeak<Point>( p, p, ((RealType<?>)dog.get()).getRealDouble(), true ) );
			}
			//IOFunctions.println("finMax true: " + peaksMax.size());
		}
		//peaks = dog2.getSubpixelPeaks(); 

		if ( params.findMinima )
		{
			final DoGDetection<FloatType> dog2 =
					new DoGDetection<>(image, interval, calibration, params.sigma, sigma2 , DoGDetection.ExtremaType.MAXIMA, InteractiveRadialSymmetry.thresholdMin, false);

			ArrayList<Point> simplePeaks = dog2.getPeaks();
			RandomAccess<?> dog = (Views.extendBorder(dog2.getDogImage())).randomAccess();
	
			for ( final Point p : simplePeaks )
			{
				dog.setPosition( p );
				peaksMin.add( new RefinedPeak<Point>( p, p, ((RealType<?>)dog.get()).getRealDouble(), true ) );
			}
			//IOFunctions.println("finMin true: " + peaksMin.size());
		}
	}

	protected final void dispose()
	{
		if ( dogWindow.getFrame() != null)
			dogWindow.getFrame().dispose();

		if (sliceObserver != null)
			sliceObserver.unregister();

		if ( imagePlus != null) {
			if (roiListener != null)
				imagePlus.getCanvas().removeMouseListener(roiListener);

			imagePlus.getOverlay().clear();
			imagePlus.updateAndDraw();
		}

		isFinished = true;
	}

	public static void main(String[] args)
	{
		File path = new File( "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/spim_TL18_Angle0.tif" );
		// path = path.concat("test_background.tif");

		if ( !path.exists() )
			throw new RuntimeException( "'" + path.getAbsolutePath() + "' doesn't exist." );

		new ImageJ();
		System.out.println( "Opening '" + path + "'");

		ImagePlus imp = new Opener().openImage( path.getAbsolutePath() );

		if (imp == null)
			throw new RuntimeException( "image was not loaded" );

		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;

		for ( int z = 1; z <= imp.getStack().getSize(); ++z )
		{
			final ImageProcessor ip = imp.getStack().getProcessor( z );

			for ( int i = 0; i < ip.getPixelCount(); ++i )
			{
				final float v = ip.getf( i );
				min = Math.min( min, v );
				max = Math.max( max, v );
			}
		}
		
		IOFunctions.println( "min=" + min );
		IOFunctions.println( "max=" + max );

		imp.show();

		imp.setSlice(20);

		new InteractiveRadialSymmetry( imp, new IDoGParams(), min, max );

		System.out.println("DOGE!");
	}
}
