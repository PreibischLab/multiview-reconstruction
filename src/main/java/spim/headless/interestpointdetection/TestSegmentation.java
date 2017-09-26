package spim.headless.interestpointdetection;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointdetection.InterestPointTools;
import spim.process.interestpointdetection.methods.dog.DoG;
import spim.process.interestpointdetection.methods.dog.DoGParameters;
import spim.process.interestpointdetection.methods.dom.DoM;
import spim.process.interestpointdetection.methods.dom.DoMParameters;

public class TestSegmentation
{
	public static void testDoG( SpimData2 spimData )
	{
		DoGParameters dog = new DoGParameters();

		dog.imgloader = spimData.getSequenceDescription().getImgLoader();
		dog.toProcess = new ArrayList< ViewDescription >();
		dog.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewDescription > removed = SpimData2.filterMissingViews( spimData, dog.toProcess );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		dog.downsampleXY = 4;
		dog.downsampleZ = 2;
		dog.sigma = 1.1;

		//dog.deviceList = spim.headless.cuda.CUDADevice.getSeparableCudaList( "lib/libSeparableConvolutionCUDALib.so" );
		//dog.cuda = spim.headless.cuda.CUDADevice.separableConvolution;

		//		DoG.findInterestPoints( dog );
		// TODO: make cuda headless
		//dog.deviceList = spim.headless.cuda.CUDADevice.getSeparableCudaList( "lib/libSeparableConvolutionCUDALib.so" );
		//dog.cuda = spim.headless.cuda.CUDADevice.separableConvolution;

		final HashMap< ViewId, List< InterestPoint > > points = DoG.findInterestPoints( dog );

		InterestPointTools.addInterestPoints( spimData, "beads", points, "DoG, sigma=1.4, downsample=2" );
	}

	public static void testDoM( final SpimData2 spimData )
	{
		DoMParameters dom = new DoMParameters();
		
		dom.imgloader = spimData.getSequenceDescription().getImgLoader();
		dom.toProcess = new ArrayList< ViewDescription >();
		dom.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewDescription > removed = SpimData2.filterMissingViews( spimData, dom.toProcess );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		dom.downsampleXY = 2;
		dom.radius1 = 2;
		
		final HashMap< ViewId, List< InterestPoint > > points = DoM.findInterestPoints( dom );
		
		InterestPointTools.addInterestPoints( spimData, "beads", points, "DoM, sigma=2, downsample=2" );
	}

	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample() );

		testDoG( spimData );
	}

}
