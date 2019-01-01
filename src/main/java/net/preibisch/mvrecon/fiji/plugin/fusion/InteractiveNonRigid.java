package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import bdv.util.BdvFunctions;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.NonRigidTools;

public class InteractiveNonRigid implements Source< VolatileFloatType >
{
	final String name;
	final ArrayList< Pair< RandomAccessibleInterval< VolatileFloatType >, AffineTransform3D > > multiRes;

	public InteractiveNonRigid(
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
		SpimData2 spimData;

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );

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

		final ArrayList< Pair< RandomAccessibleInterval< VolatileFloatType >, AffineTransform3D > > multiRes = new ArrayList<>();

		new ImageJ();

		final boolean nonRigid = false;

		IOFunctions.println( Util.printInterval( boundingBox ));
		IOFunctions.println( "nonRigid = " + nonRigid );

		for ( int downsampling = 1; downsampling <= 16; downsampling *= 2 )
		{
			IOFunctions.println( "DS: " + downsampling );

			final Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > virtualImg;

			if ( !nonRigid )
			{
				// affine
				virtualImg = FusionTools.fuseVirtual( spimData, viewIds, boundingBox, downsampling );
			}
			else
			{
				// non-rigid
				final List< ViewId > viewsToFuse = new ArrayList< ViewId >(); // fuse
				final List< ViewId > viewsToUse = new ArrayList< ViewId >(); // used to compute the non-rigid transform
	
				viewsToUse.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
				viewsToFuse.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

				final double ds = Double.isNaN( downsampling ) ? 1.0 : downsampling;
				final int cpd = Math.max( 1, Math.max( 3, (int)Math.round( 10 / ds ) ) );

				final ArrayList< String > labels = new ArrayList<>();
				labels.add( "beads13" );
				labels.add( "nuclei" );

				final int interpolation = 1;
				final long[] controlPointDistance = new long[] { cpd, cpd, cpd };
				final double alpha = 1.0;

				final boolean useBlending = true;
				final boolean useContentBased = false;
				final boolean displayDistances = false;

				final ExecutorService service = DeconViews.createExecutorService();

				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": controlPointDistance = " + Util.printCoordinates( controlPointDistance ) );

				virtualImg = NonRigidTools.fuseVirtualInterpolatedNonRigid(
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
					service );
			}

			final RandomAccessibleInterval< FloatType > cachedImg = FusionTools.cacheRandomAccessibleInterval( virtualImg.getA(), FusionGUI.maxCacheSize, new FloatType(), FusionGUI.cellDim );
			final RandomAccessibleInterval< VolatileFloatType > volatileImg = VolatileViews.wrapAsVolatile( cachedImg );
			//DisplayImage.getImagePlusInstance( virtual, true, "ds="+ds, 0, 255 ).show();
			//ImageJFunctions.show( virtualVolatile );

			multiRes.add( new ValuePair<>( volatileImg, virtualImg.getB() ) );
		}

		BdvFunctions.show( new InteractiveNonRigid( multiRes, "rendered" ) );
	}

}
