package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.net.URI;
import java.util.HashMap;

import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;

import bdv.img.n5.N5ImageLoader;
import bdv.img.n5.N5Properties;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import util.URITools;

public class AllenOMEZarrLoader extends N5ImageLoader
{
	final HashMap< ViewId, String > viewIdToPath;

	final String bucket, folder;

	public AllenOMEZarrLoader(
			final URI n5URI,
			final String bucket,
			final String folder,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription,
			final HashMap< ViewId, String > viewIdToPath )
	{
		super( URITools.instantiateN5Reader( StorageFormat.ZARR, n5URI ), n5URI, sequenceDescription );

		setNumFetcherThreads( cloudThreads );

		this.bucket = bucket;
		this.folder = folder;

		this.viewIdToPath = viewIdToPath;
	}

	public AbstractSequenceDescription< ?, ?, ? > getSequenceDescription() { return seq; }
	public HashMap< ViewId, String > getViewIdToPath() { return viewIdToPath; }
	public String getBucket() { return bucket; }
	public String getFolder() { return folder; }

	@Override
	public N5Properties createN5PropertiesInstance()
	{
		return new AllenOMEZarrProperties( this );
	}

	public static void main( String[] args ) throws SpimDataException
	{
		URI xml = URITools.toURI( "/Users/preibischs/Documents/Janelia/Projects/BigStitcher/Allen/bigstitcher_708373/708373.xml" );
		//URI xml = URITools.toURI( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" );
		//URI xml = URITools.toURI( "gs://janelia-spark-test/I2K-test/dataset.xml" );
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
