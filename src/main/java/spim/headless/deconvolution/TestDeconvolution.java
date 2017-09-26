package spim.headless.deconvolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.deconvolution.DeconView;
import spim.process.deconvolution.DeconViewPSF.PSFTYPE;
import spim.process.deconvolution.DeconViews;
import spim.process.deconvolution.MultiViewDeconvolution;
import spim.process.deconvolution.iteration.ComputeBlockThreadCPUFactory;
import spim.process.deconvolution.iteration.ComputeBlockThreadFactory;
import spim.process.deconvolution.iteration.PsiInitialization;
import spim.process.deconvolution.iteration.PsiInitialization.PsiInit;
import spim.process.deconvolution.iteration.PsiInitializationAvgApprox;
import spim.process.deconvolution.iteration.PsiInitializationAvgPrecise;
import spim.process.deconvolution.iteration.PsiInitializationBlurredFused;
import spim.process.deconvolution.util.PSFPreparation;
import spim.process.deconvolution.util.ProcessInputImages;
import spim.process.export.DisplayImage;
import spim.process.fusion.FusionTools;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval;
import spim.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval.CombineType;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.psf.PSFCombination;

public class TestDeconvolution
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;
		Collection< Group< ViewDescription > > groups = new ArrayList<>();

		// generate 4 views with 1000 corresponding beads, single timepoint
		spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );
		groups = Group.toGroups( spimData.getSequenceDescription().getViewDescriptions().values() );

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		groups = selectViews( spimData.getSequenceDescription().getViewDescriptions().values() );
		groups = Group.toGroups( spimData.getSequenceDescription().getViewDescriptions().values() );

		/*
		final ArrayList< ViewDescription > two = new ArrayList<>();
		two.add( spimData.getSequenceDescription().getViewDescriptions().get( new ViewId( 0,0 ) ) );
		two.add( spimData.getSequenceDescription().getViewDescriptions().get( new ViewId( 0,1 ) ) );
		groups = oneGroupPerView( two );
		*/
		testDeconvolution( spimData, groups, "My Bounding Box111" );
		// for bounding box1111 test 128,128,128 vs 256,256,256 (no blocks), there are differences at the edges
		// 
	}

	public static < V extends ViewId > void testDeconvolution(
			final SpimData2 spimData,
			final Collection< Group< V > > groups,
			final String bbTitle )
	{
		BoundingBox boundingBox = null;

		for ( final BoundingBox bb : spimData.getBoundingBoxes().getBoundingBoxes() )
			if ( bb.getTitle().equals( bbTitle ) )
				boundingBox = bb;

		if ( boundingBox == null )
		{
			System.out.println( "Bounding box '" + bbTitle + "' not found." );
			return;
		}

		IOFunctions.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		final double osemSpeedUp = 1.0;
		final double downsampling = 3.0;

		final ProcessInputImages< V > fusion = new ProcessInputImages<>(
				spimData,
				groups,
				boundingBox,
				downsampling,
				true,
				FusionTools.defaultBlendingRange,
				FusionTools.defaultBlendingBorder,
				true,
				MultiViewDeconvolution.defaultBlendingRange,
				MultiViewDeconvolution.defaultBlendingBorder / ( Double.isNaN( downsampling ) ? 1.0f : (float)downsampling ) );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Virtual Fusion of groups " );
		fusion.fuseGroups();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Normalizing weights ... " );
		fusion.normalizeWeights( osemSpeedUp, false, MultiViewDeconvolution.maxDiffRange, MultiViewDeconvolution.scalingRange );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): De-virtualization ... " );
		fusion.cacheImages();
		fusion.cacheNormalizedWeights();
		fusion.cacheUnnormalizedWeights();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Displaying " );
		//displayDebug( fusion );
		//SimpleMultiThreading.threadHaltUnClean();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading, grouping, and transforming PSF's " );

		final HashMap< Group< V >, ArrayImg< FloatType, ? > > psfs =
				PSFPreparation.loadGroupTransformPSFs( spimData.getPointSpreadFunctions(), fusion );

		//final Img< FloatType > avgPSF = PSFCombination.computeAverageImage( psfs.values(), new ArrayImgFactory< FloatType >(), true );
		//DisplayImage.getImagePlusInstance( Views.rotate( avgPSF, 0, 2 ), false, "avgPSF", 0, 1 ).show();

		final Img< FloatType > maxAvgPSF = PSFCombination.computeMaxAverageTransformedPSF( psfs.values(), new ArrayImgFactory< FloatType >() );
		DisplayImage.getImagePlusInstance( maxAvgPSF, false, "maxAvgPSF", 0, 1 ).show();

		final ImgFactory< FloatType > blockFactory = new ArrayImgFactory<>();
		final ImgFactory< FloatType > psiFactory = new ArrayImgFactory<>();
		final int[] blockSize = new int[]{ 128, 256, 256 };
		final int numIterations = 1;
		final float lambda = 0.0006f;
		final PSFTYPE psfType = PSFTYPE.INDEPENDENT;
		final boolean filterBlocksForContent = true;
		final PsiInit psiInitType = PsiInit.FUSED_BLURRED;
		final boolean debug = true;
		final int debugInterval = 1;

		// one common ExecutorService for all
		final ExecutorService service = DeconViews.createExecutorService();

		try
		{
			final ComputeBlockThreadFactory cptf = new ComputeBlockThreadCPUFactory(
					service,
					lambda,
					blockSize,
					blockFactory );

			final PsiInitialization psiInit;

			if ( psiInitType == PsiInit.FUSED_BLURRED )
				psiInit = new PsiInitializationBlurredFused();
			else if ( psiInitType == PsiInit.AVG )
				psiInit = new PsiInitializationAvgPrecise();
			else
				psiInit = new PsiInitializationAvgApprox();

			if ( filterBlocksForContent )
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution and testing for empty ones that can be dropped." );
			else
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution." );

			final ArrayList< DeconView > deconViews = new ArrayList<>();

			for ( final Group< V > group : fusion.getGroups() )
			{
				final DeconView view = new DeconView(
						service,
						fusion.getImages().get( group ),
						fusion.getNormalizedWeights().get( group ),
						psfs.get( group ),
						psfType,
						blockSize,
						cptf.numParallelBlocks(),
						filterBlocksForContent );

				if ( view.getNumBlocks() <= 0 )
					return;

				view.setTitle( Group.gvids( group ) );
				deconViews.add( view );

				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Added " + view );
			}

			final DeconViews views = new DeconViews( deconViews, service );

			final MultiViewDeconvolution decon = new MultiViewDeconvolution( views, numIterations, psiInit, cptf, psiFactory );
			if ( !decon.initWasSuccessful() )
				return;
			decon.setDebug( debug );
			decon.setDebugInterval( debugInterval );
			decon.runIterations();
			

			ImagePlus imp = DisplayImage.getImagePlusInstance( decon.getPSI(), false, "Deconvolved", Double.NaN, Double.NaN );
			imp.getCalibration().xOrigin = -(boundingBox.min( 0 ) / downsampling);
			imp.getCalibration().yOrigin = -(boundingBox.min( 1 ) / downsampling);
			imp.getCalibration().zOrigin = -(boundingBox.min( 2 ) / downsampling);
			imp.getCalibration().pixelWidth = imp.getCalibration().pixelHeight = imp.getCalibration().pixelDepth = downsampling;
			imp.show();

			service.shutdown();
		}
		catch ( OutOfMemoryError oome )
		{
			IJ.log( "Out of Memory" );
			IJ.error("Multi-View Deconvolution", "Out of memory.  Check \"Edit > Options > Memory & Threads\"");

			service.shutdown();

			return;
		}
	}

	public static < V extends ViewId > void displayDebug( final ProcessInputImages< V > fusion )
	{
		int i = 0;

		final ArrayList< RandomAccessibleInterval< FloatType > > allWeightsNormed = new ArrayList<>();

		for ( final Group< V > group : fusion.getGroups() )
		{
			System.out.println( "Img Instance: " + fusion.getImages().get( group ).getClass().getSimpleName() );
			System.out.println( "Raw Weight Instance: " + fusion.getUnnormalizedWeights().get( group ).getClass().getSimpleName() );
			System.out.println( "Normalized Weight Instance: " + fusion.getNormalizedWeights().get( group ).getClass().getSimpleName() );

			DisplayImage.getImagePlusInstance( fusion.getImages().get( group ), true, "g=" + i + " image", 0, 255 ).show();
			DisplayImage.getImagePlusInstance( fusion.getUnnormalizedWeights().get( group ), true, "g=" + i + " weightsRawDecon", 0, 2 ).show();
			DisplayImage.getImagePlusInstance( fusion.getNormalizedWeights().get( group ), true, "g=" + i + " weightsNormDecon", 0, 2 ).show();

			allWeightsNormed.add( fusion.getNormalizedWeights().get( group ) );

			// might not work if caching/copying is done before calling this method
			if ( FusedRandomAccessibleInterval.class.isInstance( fusion.getImages().get( group ) ) )
			{
				final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
				fusion.getDownsampledBoundingBox().dimensions( dim );
	
				DisplayImage.getImagePlusInstance(
						new CombineWeightsRandomAccessibleInterval(
								new FinalInterval( dim ),
								((FusedRandomAccessibleInterval)fusion.getImages().get( group )).getWeights(),
								CombineType.SUM ),
						true,
						"g=" + i + " weightsFusion",
						0, 1 ).show();
			}

			++i;
		}

		// display the sum of all normed weights
		final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
		fusion.getDownsampledBoundingBox().dimensions( dim );

		DisplayImage.getImagePlusInstance(
				new CombineWeightsRandomAccessibleInterval(
						new FinalInterval( dim ),
						allWeightsNormed,
						CombineType.SUM ),
				true,
				"sum of all normed weights",
				0, 1 ).show();
	}

	public static ArrayList< Group< ViewDescription > > selectViews( final Collection< ViewDescription > views )
	{
		final ArrayList< Group< ViewDescription > > groups = new ArrayList<>();

		final Group< ViewDescription > angle0and180 = new Group<>();
		final Group< ViewDescription > angle45and225 = new Group<>();
		final Group< ViewDescription > angle90and270 = new Group<>();

		for ( final ViewDescription vd : views )
		{
			final int angle = Integer.parseInt( vd.getViewSetup().getAngle().getName() );

			if ( angle == 0 || angle == 180 )
				angle0and180.getViews().add( vd );

			if ( angle == 45 || angle == 225 )
				angle45and225.getViews().add( vd );

			if ( angle == 90 || angle == 270 )
				angle90and270.getViews().add( vd );
		}

		groups.add( angle0and180 );
		groups.add( angle45and225 );
		groups.add( angle90and270 );

		System.out.println( "Views remaining:" );
		for ( final Group< ViewDescription > group : groups )
			System.out.println( group );

		return groups;
	}
}
