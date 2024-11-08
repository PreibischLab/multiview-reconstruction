package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.net.URI;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata.OmeNgffDataset;

import util.URITools;

public class OMEZarrAttibutes
{
	public static void loadOMEZarr( final N5Reader n5, final String dataset )
	{
		//org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata
		// for this to work you need to register an adapter in the N5Factory class
		// final GsonBuilder builder = new GsonBuilder().registerTypeAdapter( CoordinateTransformation.class, new CoordinateTransformationAdapter() );
		final OmeNgffMultiScaleMetadata[] multiscales = n5.getAttribute( dataset, "multiscales", OmeNgffMultiScaleMetadata[].class );

		if ( multiscales == null || multiscales.length == 0 )
			throw new RuntimeException( "Could not parse OME-ZARR multiscales object. stopping." );

		if ( multiscales.length != 1 )
			System.out.println( "This dataset has " + multiscales.length + " objects, we expected 1. Picking the first one." );

		//System.out.println( "AllenOMEZarrLoader.getMipmapResolutions() for " + setupId + " using " + n5properties.getPath( setupId, timePointId ) + ": found " + multiscales[ 0 ].datasets.length + " multi-resolution levels." );

		//double[][] mipMapResolutions = new double[ multiscales[ 0 ].datasets.length ][ 3 ];
		//double[] firstScale = null;

		for ( int i = 0; i < multiscales[ 0 ].datasets.length; ++i )
		{
			final OmeNgffDataset ds = multiscales[ 0 ].datasets[ i ];
			System.out.println( ds.coordinateTransformations.length );
		}
	}

	public static void main( String[] args )
	{
		final URI uri = URITools.toURI( "s3://aind-open-data/exaSPIM_708373_2024-04-02_19-49-38/SPIM.ome.zarr/" );
		final String dataset = "tile_x_0001_y_0001_z_0000_ch_488.zarr";

		//final URI uri = URITools.toURI( "/nrs/cellmap/data/jrc_cos7-11/jrc_cos7-11.zarr/" );
		//final String dataset = "recon-2/lm/er_palm/";
		//final String dataset = "recon-1/em/fibsem-uint16/";

		final N5Reader n5 = URITools.instantiateN5Reader( StorageFormat.ZARR, uri );

		loadOMEZarr(n5, dataset);
	}
}
