package net.preibisch.mvrecon.headless.fusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.NonRigidTools;

public class TestNonRigid
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		//spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Desktop/i2k/sim2/dataset.xml" );
		//spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Downloads/x-wing/dataset.xml" );

		Pair< List< ViewId >, BoundingBox > fused = testInterpolation( spimData, "My Bounding Box" );
		// for bounding box1111 test 128,128,128 vs 256,256,256 (no blocks), there are differences at the edges

		compareToFusion( spimData, fused.getA(), fused.getB() );
	}

	public static void compareToFusion(
			final SpimData2 spimData,
			final List< ViewId > fused,
			final BoundingBox boundingBox )
	{
		// downsampling
		double downsampling = Double.NaN;

		//
		// display virtually fused
		//
		final RandomAccessibleInterval< FloatType > virtual = FusionTools.fuseVirtual( spimData, fused, boundingBox, downsampling ).getA();
		DisplayImage.getImagePlusInstance( virtual, true, "Fused Affine", 0, 255 ).show();
	}

	public static Pair< List< ViewId >, BoundingBox > testInterpolation(
			final SpimData2 spimData,
			final String bbTitle )
	{
		final BoundingBox boundingBox = TestBoundingBox.getBoundingBox( spimData, bbTitle );

		if ( boundingBox == null )
			return null;

		IOFunctions.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		// select views to process
		final List< ViewId > viewsToFuse = new ArrayList< ViewId >(); // fuse
		final List< ViewId > viewsToUse = new ArrayList< ViewId >(); // used to compute the non-rigid transform

		viewsToUse.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		//viewsToFuse.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
		viewsToFuse.add( new ViewId( 0, 0 ) );
		viewsToFuse.add( new ViewId( 0, 1 ) );
		viewsToFuse.add( new ViewId( 0, 2 ) );
		viewsToFuse.add( new ViewId( 0, 3 ) );

		// filter not present ViewIds
		List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewsToUse );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		removed = SpimData2.filterMissingViews( spimData, viewsToFuse );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// downsampling
		final double downsampling = 2.0;
		final double ds = Double.isNaN( downsampling ) ? 1.0 : downsampling;
		final int cpd = Math.max( 1, (int)Math.round( 10 / ds ) );
		//
		// display virtually fused
		//
		final ArrayList< String > labels = new ArrayList<>();

		//labels.add( "beads" );

		labels.add( "beads13" );
		//labels.add( "nuclei" );

		final int interpolation = 1;
		final long[] controlPointDistance = new long[] { cpd, cpd, cpd };
		final double alpha = 1.0;

		final boolean useBlending = true;
		final boolean useContentBased = false;
		final boolean displayDistances = false;

		final ExecutorService service = DeconViews.createExecutorService();

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": controlPointDistance = " + Util.printCoordinates( controlPointDistance ) );

		final RandomAccessibleInterval< FloatType > virtual =
				NonRigidTools.fuseVirtualInterpolatedNonRigid(
						spimData,
						viewsToFuse,
						viewsToUse,
						labels,
						useBlending,
						useContentBased,
						displayDistances,
						controlPointDistance,
						alpha,
						interpolation,
						boundingBox,
						downsampling,
						null,
						service ).getA();

		DisplayImage.getImagePlusInstance( virtual, true, "Fused Non-rigid", 0, 255 ).show();

		return new ValuePair<>( viewsToFuse, boundingBox );
	}
}
