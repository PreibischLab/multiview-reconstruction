package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ForkJoinPool;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata.OmeNgffDataset;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.ScaleCoordinateTransformation;

import bdv.ViewerImgLoader;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.img.n5.N5ImageLoader;
import bdv.img.n5.N5Properties;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import util.URITools;

public class AllenOMEZarrLoader extends N5ImageLoader
{
	final HashMap< ViewId, String > viewIdToPath;
	final HashMap< Integer, double[][] > setupIdToMultiRes;
	final HashMap< Integer, DataType > setupIdToDataType;

	public AllenOMEZarrLoader( final URI n5URI, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription, final HashMap< ViewId, String > viewIdToPath )
	{
		this( URITools.instantiateN5Reader( StorageFormat.ZARR, n5URI ), n5URI, sequenceDescription, viewIdToPath );
	}

	public AllenOMEZarrLoader( final N5Reader n5, final URI n5URI, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription, final HashMap< ViewId, String > viewIdToPath )
	{
		super( n5, n5URI, sequenceDescription );

		this.viewIdToPath = viewIdToPath;

		final HashSet< Integer > viewSetupIds = new HashSet<>();
		viewIdToPath.keySet().forEach( viewId -> viewSetupIds.add( viewId.getViewSetupId() ) );

		this.setupIdToMultiRes = new HashMap<>();
		this.setupIdToDataType = new HashMap<>();

		System.out.println( "Loading metadata from OME-ZARR containers (in parallel) ... " );

		// assemble metadata in advance in parallel (we should store this to the XML)
		final ForkJoinPool myPool = new ForkJoinPool( URITools.cloudThreads );

		myPool.submit(() -> viewSetupIds.parallelStream().forEach( setupId ->
		{
			final int timePointId = getRandomTimepointId( setupId );

			// multiresolution pyramid

			//org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata
			// for this to work you need to register an adapter in the N5Factory class
			// final GsonBuilder builder = new GsonBuilder().registerTypeAdapter( CoordinateTransformation.class, new CoordinateTransformationAdapter() );
			final OmeNgffMultiScaleMetadata[] multiscales = n5.getAttribute( n5properties.getPath( setupId, timePointId ), "multiscales", OmeNgffMultiScaleMetadata[].class );

			if ( multiscales == null || multiscales.length == 0 )
				throw new RuntimeException( "Could not parse OME-ZARR multiscales object. stopping." );

			if ( multiscales.length != 1 )
				System.out.println( "This dataset has " + multiscales.length + " objects, we expected 1. Picking the first one." );

			//System.out.println( "AllenOMEZarrLoader.getMipmapResolutions() for " + setupId + " using " + n5properties.getPath( setupId, timePointId ) + ": found " + multiscales[ 0 ].datasets.length + " multi-resolution levels." );

			double[][] mipMapResolutions = new double[ multiscales[ 0 ].datasets.length ][ 3 ];
			double[] firstScale = null;

			for ( int i = 0; i < multiscales[ 0 ].datasets.length; ++i )
			{
				final OmeNgffDataset ds = multiscales[ 0 ].datasets[ i ];

				for ( final CoordinateTransformation< ? > c : ds.coordinateTransformations )
				{
					if ( ScaleCoordinateTransformation.class.isInstance( c ) )
					{
						final ScaleCoordinateTransformation s = (ScaleCoordinateTransformation)c;

						if ( firstScale == null )
							firstScale = s.getScale().clone();

						for ( int d = 0; d < mipMapResolutions[ i ].length; ++d )
						{
							mipMapResolutions[ i ][ d ] = s.getScale()[ d ] / firstScale[ d ];
							mipMapResolutions[ i ][ d ] = Math.round(mipMapResolutions[ i ][ d ]*10000)/10000d; // round to the 5th digit
						}
						//System.out.println( "AllenOMEZarrLoader.getMipmapResolutions(), level " + i + ": " + Arrays.toString( s.getScale() ) + " >> " + Arrays.toString( mipMapResolutions[ i ] ) );
					}
				}
			}

			setupIdToMultiRes.put( setupId, mipMapResolutions );

			// data type
			final DatasetAttributes attrib =
					n5.getDatasetAttributes( n5properties.getPath( setupId, timePointId, 0 ) );

			//System.out.println( "AllenOMEZarrLoader.getDataType() for " + setupId + " using " + n5properties.getPath( setupId, timePointId ) + ": " + attrib.getDataType() );

			setupIdToDataType.put( setupId, attrib.getDataType() );
		})).join();

		myPool.shutdown();

		viewSetupIds.stream().sorted().forEach( setupId ->
		{
			System.out.println( "ViewSetupId: " + setupId + ": " + setupIdToDataType.get( setupId ) + ", " + Arrays.deepToString( setupIdToMultiRes.get( setupId ) ) );
		});

		// more threads for cloud-based fetching
		System.out.println( "Setting num fetcher threads to " + URITools.cloudThreads + " for cloud access." );
		setNumFetcherThreads( URITools.cloudThreads );
	}

	public int getRandomTimepointId( final int setupId )
	{
		return getRandomTimepointId( viewIdToPath, setupId );
	}

	public static int getRandomTimepointId( final HashMap< ViewId, String > viewIdToPath, final int setupId )
	{
		// find any timepoint of this setupId
		ViewId v = null;

		for ( final ViewId viewId : viewIdToPath.keySet() )
			if ( viewId.getViewSetupId() == setupId )
			{
				v = viewId;
				break;
			}

		if ( v == null )
			throw new RuntimeException( "Could not find a ViewSetup that contains SetupId: " + setupId );

		return v.getTimePointId();
	}

	public N5Properties createN5PropertiesInstance()
	{
		return new N5Properties()
		{
			@Override
			public String getPath( final int setupId )
			{
				return ".";
			}
	
			@Override
			public String getPath( final int setupId, final int timepointId )
			{
				return viewIdToPath.get( new ViewId(timepointId, setupId) );
			}
	
			@Override
			public String getPath( final int setupId, final int timepointId, final int level )
			{
				return String.format( getPath( setupId, timepointId )+ "/%d", level );
			}

			@Override
			public DataType getDataType( final N5Reader n5, final int setupId )
			{
				return setupIdToDataType.get( setupId );
			}

			@Override
			public double[][] getMipmapResolutions( final N5Reader n5, final int setupId )
			{
				return setupIdToMultiRes.get( setupId );
			}

			@Override
			public <T extends NativeType<T>> RandomAccessibleInterval<T> extractImg(
					final VolatileCachedCellImg<T, ?> img,
					final int setupId,
					final int timepointId)
			{
				return Views.hyperSlice( Views.hyperSlice( img, 4, 0 ), 3, 0 );
			}
		};
	}

	public static void main( String[] args ) throws SpimDataException
	{
		URI xml = URITools.toURI( "/Users/preibischs/Documents/Janelia/Projects/BigStitcher/Allen/bigstitcher_708373/708373.xml" );
		//URI xml = URITools.toURI( "/Users/preibischs/SparkTest/IP/dataset.xml" );

		XmlIoSpimData2 io = new XmlIoSpimData2();
		SpimData2 data = io.load( xml );

		SetupImgLoader sil = (SetupImgLoader)data.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );

		final int tp = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 ).getId();

		//RandomAccessibleInterval img = sil.getImage( tp, sil.getMipmapResolutions().length - 1 );
		RandomAccessibleInterval vol = sil.getVolatileImage( tp, sil.getMipmapResolutions().length - 1 );

		new ImageJ();
		//ImageJFunctions.show( img );
		ImageJFunctions.show( vol );

		final ViewSetupExplorer< SpimData2 > explorer = new ViewSetupExplorer<>( data, xml, io );
		explorer.getFrame().toFront();
	}
}
