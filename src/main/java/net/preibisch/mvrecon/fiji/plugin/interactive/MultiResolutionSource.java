package net.preibisch.mvrecon.fiji.plugin.interactive;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class MultiResolutionSource implements Source< VolatileFloatType >
{
	final String name;
	final ArrayList< Pair< RandomAccessibleInterval< VolatileFloatType >, AffineTransform3D > > multiRes;

	public MultiResolutionSource(
			final ArrayList< Pair< RandomAccessibleInterval< VolatileFloatType >, AffineTransform3D > > multiRes,
			final String name )
	{
		this.multiRes = multiRes;
		this.name = name;
	}

	@Override
	public boolean isPresent( final int t )
	{
		if ( t == 0 )
			return true;
		else
			return false;
	}

	@Override
	public RandomAccessibleInterval< VolatileFloatType > getSource( final int t, final int level )
	{
		if ( t != 0 )
			return null;
		else
			return multiRes.get( level ).getA();
	}

	@Override
	public RealRandomAccessible< VolatileFloatType > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		if ( t != 0 )
			return null;
		else if ( method == Interpolation.NEARESTNEIGHBOR )
			return Views.interpolate( Views.extendZero( multiRes.get( level ).getA() ), new NearestNeighborInterpolatorFactory<>() );
		else
			return Views.interpolate( Views.extendZero( multiRes.get( level ).getA() ), new NLinearInterpolatorFactory<>() );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		if ( t != 0 )
			return;
		else
			transform.set( multiRes.get( level ).getB() );
	}

	@Override
	public VolatileFloatType getType()
	{
		return new VolatileFloatType();
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return new FinalVoxelDimensions( "um", 1.0, 1.0, 1.0 );
	}

	@Override
	public int getNumMipmapLevels()
	{
		return multiRes.size();
	}

	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;

		// load drosophila
		if ( System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		else
			spimData = new XmlIoSpimData2( "" ).load( "/home/steffi/Desktop/HisYFP-SPIM/dataset.xml" );

		final BoundingBox boundingBox = TestBoundingBox.getBoundingBox( spimData, "My Bounding Box" );

		if ( boundingBox == null )
			return;

		IOFunctions.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final int minDS = 1;
		final int maxDS = 16;
		final int dsInc = 2;

		// affine
		final ArrayList< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > multiResAffine =
				MultiResolutionTools.createMultiResolutionAffine( spimData, viewIds, boundingBox, minDS, maxDS, dsInc );

		// non-rigid
		final List< ViewId > viewsToFuse = new ArrayList< ViewId >(); // fuse
		final List< ViewId > viewsToUse = new ArrayList< ViewId >(); // used to compute the non-rigid transform

		viewsToUse.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
		viewsToFuse.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		final ArrayList< String > labels = new ArrayList<>();
		labels.add( "beads13" );
		labels.add( "nuclei" );

		final int interpolation = 1;
		final long cpd = 10;
		final double alpha = 1.0;

		final boolean useBlending = true;
		final boolean useContentBased = false;
		final boolean displayDistances = false;

		final ExecutorService service = DeconViews.createExecutorService();

		final ArrayList< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > multiResNonRigid =
				MultiResolutionTools.createMultiResolutionNonRigid(
						spimData,
						viewsToFuse,
						viewsToUse,
						labels,
						useBlending,
						useContentBased,
						displayDistances,
						cpd,
						alpha,
						interpolation,
						boundingBox,
						null,
						service,
						minDS,
						maxDS,
						dsInc );
		
		service.shutdown();

		/*
		for ( int i = 0; i < multiResNonRigid.size(); ++i )
		{
			final ImagePlus imp = DisplayImage.getImagePlusInstance( multiResNonRigid.get( i ).getA(), true, "nonrigid_"+i, 0, 255 );
			imp.setSlice( imp.getStackSize() / 2 );
			imp.show();
		}
		*/

		//ImageJFunctions.show( multiResAffine.get( 0 ).getA() ).setTitle( "affine" );;
		//ImageJFunctions.show( multiResNonRigid.get( 0 ).getA() ).setTitle( "nonrigid" );;

		BdvOptions options = Bdv.options().numSourceGroups( 2 ).frameTitle( "Affine vs. NonRigid" );

		BdvStackSource< ? > affine = BdvFunctions.show( new MultiResolutionSource( MultiResolutionTools.createVolatileRAIs( multiResAffine ), "affine" ), options );
		final double[] minmax = FusionTools.minMaxApprox( multiResAffine.get( multiResAffine.size() - 1 ).getA() );
		affine.setDisplayRange( minmax[ 0 ], minmax[ 1 ] );
		affine.setColor( new ARGBType( ARGBType.rgba( 255, 0, 255, 0 ) ) );
		MultiResolutionTools.updateBDV( affine );

		options.addTo( affine );
		BdvStackSource< ? > nr = BdvFunctions.show( new MultiResolutionSource( MultiResolutionTools.createVolatileRAIs( multiResNonRigid ), "nonrigid" ), options );
		nr.setDisplayRange( minmax[ 0 ], minmax[ 1 ] );
		nr.setColor( new ARGBType( ARGBType.rgba( 0, 255, 0, 0 ) ) );
		MultiResolutionTools.updateBDV( nr );
		

		/*
		Random rnd = new Random();
	
		for ( int i = 0; i >= 0; ++i )
		{
			s.setColor( new ARGBType( ARGBType.rgba( rnd.nextDouble()*255, rnd.nextDouble()*255, rnd.nextDouble()*255, 0 ) ) );
			SimpleMultiThreading.threadWait( 3000 );
		}*/
	}

}
