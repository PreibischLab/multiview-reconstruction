package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata.OmeNgffDataset;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.ScaleCoordinateTransformation;

import bdv.img.cache.VolatileCachedCellImg;
import bdv.img.n5.N5Properties;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.view.Views;

public class AllenOMEZarrProperties implements N5Properties
{
	final AllenOMEZarrLoader loader;

	public AllenOMEZarrProperties( final AllenOMEZarrLoader loader )
	{
		this.loader = loader;
	}

	@Override
	public String getPath( final int setupId )
	{
		// that does not really exist
		return "";
	}

	@Override
	public String getPath( final int setupId, final int timepointId )
	{
		return loader.viewIdToPath.get( new ViewId( timepointId, setupId ) );
	}

	@Override
	public String getPath( final int setupId, final int timepointId, final int level )
	{
		return String.format( getPath( setupId, timepointId )+ "/%d", level );
	}

	@Override
	public DataType getDataType( final N5Reader n5, final int setupId )
	{
		return getDataType( this, n5, setupId );
	}

	@Override
	public double[][] getMipmapResolutions( final N5Reader n5, final int setupId )
	{
		return getMipMapResolutions( this, n5, setupId );
	}

	@Override
	public <T extends NativeType<T>> RandomAccessibleInterval<T> extractImg(
			final VolatileCachedCellImg<T, ?> img,
			final int setupId,
			final int timepointId)
	{
		return Views.hyperSlice( Views.hyperSlice( img, 4, 0 ), 3, 0 );
	}

	@Override
	public DatasetAttributes getDatasetAttributes( final N5Reader n5, final String pathName )
	{
		return n5.getDatasetAttributes( pathName );
	}

	//
	// static methods
	//

	public static int getFirstAvailableTimepointId( final AbstractSequenceDescription< ?, ?, ? > seq, final int setupId )
	{
		for ( final TimePoint tp : seq.getTimePoints().getTimePointsOrdered() )
		{
			if ( !seq.getMissingViews().getMissingViews().contains( new ViewId( tp.getId(), setupId ) ) )
				return tp.getId();
		}

		throw new RuntimeException( "All timepoints for setupId " + setupId + " are declared missing. Stopping." );
	}

	public static DataType getDataType( final AllenOMEZarrProperties n5properties, final N5Reader n5, final int setupId )
	{
		final int timePointId = getFirstAvailableTimepointId( n5properties.loader.getSequenceDescription(), setupId );
		return n5.getDatasetAttributes( n5properties.getPath( setupId, timePointId, 0 ) ).getDataType();
	}

	public static double[][] getMipMapResolutions( final AllenOMEZarrProperties n5properties, final N5Reader n5, final int setupId )
	{
		final int timePointId = getFirstAvailableTimepointId( n5properties.loader.getSequenceDescription(), setupId );

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

		return mipMapResolutions;
	}
}
