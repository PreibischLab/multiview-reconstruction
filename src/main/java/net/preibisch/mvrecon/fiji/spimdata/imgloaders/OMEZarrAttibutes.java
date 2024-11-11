/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2024 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.net.URI;
import java.util.function.Function;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata.OmeNgffDataset;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata.OmeNgffDownsamplingMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.ScaleCoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.TranslationCoordinateTransformation;

import net.imglib2.realtransform.AffineTransform3D;
import util.URITools;

public class OMEZarrAttibutes
{
	/**
	 * 
	 * @param n - num dimensions, 3-5 makes sense here (zyx to tczyx; TODO: which order?)
	 * @param numResolutionLevels - number of multiresolution levels (e.g. s0, s1, s2 would be 3), always includes full res
	 * @return
	 */
	public static OmeNgffMultiScaleMetadata[] createOMEZarrMetadata(
			final int n,
			final int numResolutionLevels,
			final Function<Integer, String> levelToName,
			final Function<Integer, AffineTransform3D> levelToMipmapTransform )
	{
		final OmeNgffMultiScaleMetadata[] meta = new OmeNgffMultiScaleMetadata[ 1 ];

		// dataset name
		final String path = null;
		final String name = "setup0_timepoint5"; // I also saw "/"
		final String type = null;

		// axis descriptions
		// TODO: for some reason they are in TCZYX order
		final Axis[] axes = new Axis[ n ];

		int index = 0;

		if ( n >= 5 )
			axes[ index++ ] = new Axis( "time", "t", "millisecond" );

		if ( n >= 4 )
			axes[ index++ ] = new Axis( "channel", "c", null );

		axes[ index++ ] = new Axis( "space", "z", "micrometer" );
		axes[ index++ ] = new Axis( "space", "y", "micrometer" );
		axes[ index++ ] = new Axis( "space", "x", "micrometer" );

		// multiresolution-pyramid
		// TODO: for some reason in XYZCT order (but in the file it seems reversed)
		final OmeNgffDataset[] datasets = new OmeNgffDataset[ numResolutionLevels ];

		for ( int s = 0; s < numResolutionLevels; ++s )
		{
			datasets[ s ] = new OmeNgffDataset();

			datasets[ s ].path = levelToName.apply( s );

			datasets[ s ].coordinateTransformations = new CoordinateTransformation[ 2 ];
			datasets[ s ].coordinateTransformations[ 0 ] = new ScaleCoordinateTransformation( new double[] { } ); // TODO
			datasets[ s ].coordinateTransformations[ 1 ] = new TranslationCoordinateTransformation( new double[] { } ); // TODO
		}

		// just saw these being null everywhere
		final DatasetAttributes[] childrenAttributes = null;
		final CoordinateTransformation<?>[] coordinateTransformations = null; // I also saw ScaleTransform [1,1,1]
		final OmeNgffDownsamplingMetadata metadata = null;
		
		meta[ 0 ] = new OmeNgffMultiScaleMetadata( n, path, name, type, "0.4", axes, datasets, childrenAttributes, coordinateTransformations, metadata );

		return meta;
	}

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
		//final URI uri = URITools.toURI( "https://storage.googleapis.com/jax-public-ngff/KOMP/adult_lacZ/ndp/Moxd1/23420_K35061_FGut.zarr/0/" );
		//final String dataset = "/";

		final URI uri = URITools.toURI( "s3://aind-open-data/exaSPIM_708373_2024-04-02_19-49-38/SPIM.ome.zarr/" );
		final String dataset = "tile_x_0001_y_0001_z_0000_ch_488.zarr";

		//final URI uri = URITools.toURI( "/nrs/cellmap/data/jrc_cos7-11/jrc_cos7-11.zarr/" );
		//final String dataset = "recon-2/lm/er_palm/";
		//final String dataset = "recon-1/em/fibsem-uint16/";

		final N5Reader n5 = URITools.instantiateN5Reader( StorageFormat.ZARR, uri );

		loadOMEZarr(n5, dataset);
	}
}
