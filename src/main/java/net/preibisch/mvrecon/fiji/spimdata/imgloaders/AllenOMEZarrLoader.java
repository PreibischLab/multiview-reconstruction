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
import java.util.Map;

import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;

import bdv.ViewerSetupImgLoader;
import bdv.img.n5.N5ImageLoader;
import bdv.img.n5.N5Properties;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicMultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import util.URITools;

public class AllenOMEZarrLoader extends N5ImageLoader
{
	private final AbstractSequenceDescription< ?, ?, ? > sequenceDescription;

	private final Map< ViewId, String > viewIdToPath;

	private final String bucket, folder;

	public AllenOMEZarrLoader(
			final URI n5URI,
			final String bucket,
			final String folder,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription,
			final Map< ViewId, String > viewIdToPath )
	{
		super( URITools.instantiateN5Reader( StorageFormat.ZARR, n5URI ), n5URI, sequenceDescription );
		this.sequenceDescription = sequenceDescription;

		this.bucket = bucket;
		this.folder = folder;

		this.viewIdToPath = viewIdToPath;
	}

	public Map< ViewId, String > getViewIdToPath()
	{
		return viewIdToPath;
	}

	public String getBucket()
	{
		return bucket;
	}

	public String getFolder()
	{
		return folder;
	}

	@Override
	protected N5Properties createN5PropertiesInstance()
	{
		return new AllenOMEZarrProperties( sequenceDescription, viewIdToPath );
	}

	@Override
	protected < T extends NativeType< T > > RandomAccessibleInterval< T > prepareCachedImage(
			final String datasetPath,
			final int setupId, final int timepointId, final int level,
			final CacheHints cacheHints,
			final T type )
	{
		return super.prepareCachedImage( datasetPath, setupId, 0, level, cacheHints, type ).view()
				.slice( 4, 0 )
				.slice( 3, 0 );
	}

	public static void main( String[] args ) throws SpimDataException
	{
		URI xml = URITools.toURI( "/Users/preibischs/Documents/Janelia/Projects/BigStitcher/Allen/bigstitcher_708373/708373.split.xml" );
		//URI xml = URITools.toURI( "/Users/preibischs/Documents/Janelia/Projects/BigStitcher/Allen/bigstitcher_708373/708373.xml" );
		//URI xml = URITools.toURI( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" );
		//URI xml = URITools.toURI( "gs://janelia-spark-test/I2K-test/dataset.xml" );
		//URI xml = URITools.toURI( "/Users/preibischs/SparkTest/IP/dataset.xml" );

		XmlIoSpimData2 io = new XmlIoSpimData2();
		SpimData2 data = io.load( xml );

		System.out.println( "Setting cloud fetcher threads to: " + URITools.cloudThreads );
		URITools.setNumFetcherThreads( data.getSequenceDescription().getImgLoader(), URITools.cloudThreads );

		System.out.println( "Prefetching with threads: " + URITools.cloudThreads );
		URITools.prefetch( data.getSequenceDescription().getImgLoader(), URITools.cloudThreads );

		//imgL.setNumFetcherThreads( cloudThreads );
		ViewerSetupImgLoader sil = (ViewerSetupImgLoader)data.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );

		final int tp = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 ).getId();

		//RandomAccessibleInterval img = sil.getImage( tp, sil.getMipmapResolutions().length - 1 );
		RandomAccessibleInterval vol = sil.getVolatileImage( tp, ((BasicMultiResolutionSetupImgLoader)sil).getMipmapResolutions().length - 1 );

		new ImageJ();
		//ImageJFunctions.show( img );
		ImageJFunctions.show( vol );

		final ViewSetupExplorer< SpimData2 > explorer = new ViewSetupExplorer<>( data, xml, io );
		explorer.getFrame().toFront();
	}
}
