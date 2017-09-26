package spim.headless.psf;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.headless.registration.TestRegistration;
import spim.process.export.DisplayImage;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.psf.PSFCombination;
import spim.process.psf.PSFExtraction;

public class TestPSF
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testPSF( spimData, true );
	}

	public static void testPSF( final SpimData2 spimData, final boolean display )
	{
		new ImageJ();

		// run registration
		TestRegistration.testRegistration( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final String label = "beads"; // this could be different for each ViewId

		final HashMap< ViewId, Img< FloatType > > psfs = new HashMap<>();

		for ( final ViewId viewId : viewIds )
		{
			final PSFExtraction< FloatType > psf = new PSFExtraction< FloatType >( spimData, viewId, label, true, new FloatType(), new long[]{ 19, 19, 25 } );

			psf.removeMinProjections();

			spimData.getViewRegistrations().getViewRegistration( viewId ).updateModel();
			psfs.put( viewId, psf.getTransformedNormalizedPSF( spimData.getViewRegistrations().getViewRegistration( viewId ).getModel() ) );

			//ImageJFunctions.show( psf.getPSF() );
			//ImageJFunctions.show( psfs.get( viewId ) );
		}

		if ( display )
		{
			final Img< FloatType > avgPSF = PSFCombination.computeAverageImage( psfs.values(), new ArrayImgFactory< FloatType >(), true );
			final Img< FloatType > maxAvgPSF = PSFCombination.computeMaxAverageTransformedPSF( psfs.values(), new ArrayImgFactory< FloatType >() );

			DisplayImage.getImagePlusInstance( Views.rotate( avgPSF, 0, 2 ), false, "avgPSF", 0, 1 ).show();
			DisplayImage.getImagePlusInstance( maxAvgPSF, false, "maxAvgPSF", 0, 1 ).show();
		}
	}
}
