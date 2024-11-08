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
package util;

import static mpicbg.spim.data.XmlKeys.SPIMDATA_TAG;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudUtils;
import org.janelia.saalfeldlab.n5.GsonKeyValueN5Reader;
import org.janelia.saalfeldlab.n5.KeyValueAccess;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.s3.AmazonS3Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformationAdapter;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import com.google.gson.GsonBuilder;

import bdv.ViewerImgLoader;
import bdv.img.n5.N5ImageLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.SpimDataIOException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting.SplitViewerImgLoader;

public class URITools
{
	private final static Pattern HTTPS_SCHEME = Pattern.compile( "http(s)?", Pattern.CASE_INSENSITIVE );
	private final static Pattern FILE_SCHEME = Pattern.compile( "file", Pattern.CASE_INSENSITIVE );

	public static int cloudThreads = 256;

	public static boolean useS3CredentialsWrite = true;
	public static boolean useS3CredentialsRead = true;

	public static URI getParentURINoEx( final URI uri )
	{
		try
		{
			return getParentURI(uri);
		}
		catch (SpimDataIOException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	public static URI getParentURI( final URI uri ) throws SpimDataIOException
	{
		try
		{
			final String uriPath = uri.getPath();
			final int parentSlash = uriPath.lastIndexOf( "/", uriPath.length() - 2 );
			if ( parentSlash < 0 )
			{
				throw new SpimDataIOException( "URI is already at the root" );
			}
			// NB: The "+ 1" below is *very important*, so that the resultant URI
			// ends in a trailing slash. The behaviour of URI differs depending on
			// whether this trailing slash is present; specifically:
			//
			// * new URI("file:/foo/bar/").resolve(".") -> "file:/foo/bar/"
			// * new URI("file:/foo/bar").resolve(".") -> "file:/foo/"
			//
			// That is: /foo/bar/ is considered to be in the directory /foo/bar,
			// whereas /foo/bar is considered to be in the directory /foo.
			final String parentPath = uriPath.substring( 0, parentSlash + 1 );
			return new URI( uri.getScheme(), uri.getUserInfo(), uri.getHost(),
				uri.getPort(), parentPath, uri.getQuery(), uri.getFragment() );
		}
		catch ( URISyntaxException e )
		{
			throw new SpimDataIOException( e );
		}
	}

	/**
	 * A little hack to get a generic KeyValueAccess for a cloud store
	 * 
	 * @param uri - the full URI (even though only scheme and host/bucket will be used)
	 * @return the KeyValueStore
	 */
	public static KeyValueAccess getKeyValueAccess( final URI uri )
	{
		if ( URITools.isS3( uri ) || URITools.isGC( uri ) )
		{
			try
			{
				final URI bucket = new URI( uri.getScheme(), uri.getHost(), null, null );
				final N5Reader n5 = instantiateN5Reader(StorageFormat.N5, bucket );
				return ((GsonKeyValueN5Reader)n5).getKeyValueAccess();
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
				throw new RuntimeException( "An unexpected URI syntax error occured for '" + uri + "': " + e );
			}
		}
		else
		{
			if ( uri.getScheme() != null )
				throw new RuntimeException( "Unsupported uri scheme: " + uri.getScheme() + " in '" + uri + "'." );
			else
				throw new RuntimeException( "Cannot get a KeyValueAccess for a relative path '" + uri + "'." );
		}
	}

	public static void saveSpimData( final SpimData2 data, final URI xmlURI, final XmlIoSpimData2 io ) throws SpimDataException
	{
		if ( URITools.isFile( xmlURI ) )
		{
			data.setBasePathURI( getParentURI( xmlURI ) );

			// old code for filesystem
			io.save( data, URITools.fromURI( xmlURI ) );
		}
		else if ( URITools.isS3( xmlURI ) || URITools.isGC( xmlURI ) )
		{
			//
			// saving the XML to s3
			//
			final KeyValueAccess kva;

			System.out.println( xmlURI );

			try
			{
				kva = URITools.getKeyValueAccess( xmlURI );
			}
			catch ( Exception e )
			{
				throw new SpimDataException( "Could not parse cloud link and setup KeyValueAccess for '" + xmlURI + "': " + e );
			}

			// fist make a copy of the XML and save it to not loose it
			try
			{
				final String xmlFile = getRelativeCloudPath( xmlURI );

				if ( kva.exists( xmlFile ) )
				{
					int maxExistingBackup = 0;
					for ( int i = 1; i < XmlIoSpimData2.numBackups; ++i )
						if ( kva.exists( xmlFile + "~" + i ) )
							maxExistingBackup = i;
						else
							break;
	
					// copy the backups
					for ( int i = maxExistingBackup; i >= 1; --i )
						URITools.copy(kva, xmlFile + "~" + i, xmlFile + "~" + (i + 1) );

					URITools.copy(kva, xmlFile, xmlFile + "~1" );
				}
			}
			catch ( Exception e )
			{
				throw new SpimDataException( "Could not save backup of XML file for '" + xmlURI + "': " + e );
			}

			try
			{
				XmlIoSpimData2.saveInterestPointsInParallel( data );
			}
			catch ( Exception e )
			{
				throw new SpimDataException( "Could not interest points for '" + xmlURI + "' in paralell: " + e );
			}

			try
			{
				XmlIoSpimData2.savePSFsInParallel( data );
			}
			catch ( Exception e )
			{
				throw new SpimDataException( "Could not point spread function for '" + xmlURI + "' in paralell: " + e );
			}

			try
			{
				final Document doc = new Document( io.toXml( data, getParentURI( xmlURI ) ) );
				final XMLOutputter xout = new XMLOutputter( Format.getPrettyFormat() );
				final String xmlString = xout.outputString( doc );

				final PrintWriter pw = openFileWriteCloudWriter( kva, xmlURI );
				pw.println( xmlString );
				pw.close();
			}
			catch ( Exception e )
			{
				throw new SpimDataException( "Could not save xml '" + xmlURI + "': " + e );
			}

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xmlURI + "'." );
		}
		else
		{
			throw new RuntimeException( "Unsupported URI: " + xmlURI );
		}
	}

	public static N5Writer instantiateN5Writer( final StorageFormat format, final URI uri )
	{
		final GsonBuilder builder = new GsonBuilder().registerTypeAdapter(
				CoordinateTransformation.class,
				new CoordinateTransformationAdapter() );

		if ( URITools.isFile( uri ) )
		{
			if ( format.equals( StorageFormat.N5 ))
				return new N5FSWriter( URITools.fromURI( uri ) );
			else if ( format.equals( StorageFormat.ZARR ))
				return new N5ZarrWriter( URITools.fromURI( uri ), builder );
			else if ( format.equals( StorageFormat.HDF5 ))
				return new N5HDF5Writer( URITools.fromURI( uri ) );
			else
				throw new RuntimeException( "Format: " + format + " not supported." );
		}
		else
		{
			N5Writer n5w;

			try
			{
				//System.out.println( "Trying writing with credentials ..." );
				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				factory.s3UseCredentials();
				n5w = factory.openWriter( format, uri );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous ..." );

				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				n5w = factory.openWriter( format, uri );
			}

			return n5w;
		}
	}

	public static N5Reader instantiateN5Reader( final StorageFormat format, final URI uri )
	{
		final GsonBuilder builder = new GsonBuilder().registerTypeAdapter(
				CoordinateTransformation.class,
				new CoordinateTransformationAdapter() );

		if ( URITools.isFile( uri ) )
		{
			if ( format.equals( StorageFormat.N5 ))
				return new N5FSReader( URITools.fromURI( uri ) );
			else if ( format.equals( StorageFormat.ZARR ))
				return new N5ZarrReader( URITools.fromURI( uri ), builder );
			else
				throw new RuntimeException( "Format: " + format + " not supported." );
		}
		else
		{
			N5Reader n5r;

			try
			{
				//System.out.println( "Trying reading with credentials ..." );
				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				factory.s3UseCredentials();
				n5r = factory.openReader( format, uri );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous with gson builder ..." );

				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				n5r = factory.openReader( format, uri );
			}

			return n5r;
		}
	}

	public static N5Reader instantiateGuessedN5Reader( final URI uri )
	{
		if ( URITools.isFile( uri ) )
		{
			if ( uri.toString().toLowerCase().endsWith( ".zarr" ) )
				return instantiateN5Reader( StorageFormat.ZARR, uri );
			else if ( uri.toString().toLowerCase().endsWith( ".n5" ) )
				return instantiateN5Reader( StorageFormat.N5, uri );
			else
				throw new RuntimeException( "Format for local storage of: " + uri + " could not be guessed (make it end in .n5 or .zarr)." );
		}
		else
		{
			N5Reader n5r;

			final GsonBuilder builder = new GsonBuilder().registerTypeAdapter(
					CoordinateTransformation.class,
					new CoordinateTransformationAdapter() );

			try
			{
				//System.out.println( "Trying reading with credentials ..." );
				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				factory.s3UseCredentials();
				n5r = factory.openReader( uri.toString() );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous ..." );
				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				n5r = factory.openReader( uri.toString() );
			}

			return n5r;
		}
	}

	public static N5Writer instantiateGuessedN5Writer( final URI uri )
	{
		if ( URITools.isFile( uri ) )
		{
			if ( uri.toString().toLowerCase().endsWith( ".zarr" ) )
				return instantiateN5Writer( StorageFormat.ZARR, uri );
			else if ( uri.toString().toLowerCase().endsWith( ".n5" ) )
				return instantiateN5Writer( StorageFormat.N5, uri );
			else
				throw new RuntimeException( "Format for local storage of: " + uri + " could not be guessed (make it end in .n5 or .zarr)." );
		}
		else
		{
			N5Writer n5w;

			final GsonBuilder builder = new GsonBuilder().registerTypeAdapter(
					CoordinateTransformation.class,
					new CoordinateTransformationAdapter() );

			try
			{
				//System.out.println( "Trying writing with credentials ..." );
				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				factory.s3UseCredentials();
				n5w = factory.openWriter( uri.toString() );
			}
			catch ( Exception e )
			{
				System.out.println( "Writing with credentials failed; trying anonymous writing ..." );
				final N5Factory factory = new N5Factory();
				factory.gsonBuilder( builder );
				n5w = factory.openWriter( uri.toString() );
			}

			return n5w;
		}
	}

	public static SpimData2 loadSpimData( final URI xmlURI, final XmlIoSpimData2 io ) throws SpimDataException
	{
		if ( URITools.isFile( xmlURI ) )
		{
			return io.load( URITools.fromURI( xmlURI ) ); // method from XmlIoAbstractSpimData
		}
		else if ( URITools.isS3( xmlURI ) || URITools.isGC( xmlURI ) )
		{
			final KeyValueAccess kva = getKeyValueAccess( xmlURI );
			final SAXBuilder sax = new SAXBuilder();
			Document doc;

			try
			{
				final InputStream is = openFileReadCloudStream(kva, xmlURI);
				doc = sax.build( is );
				is.close();
			}
			catch ( final Exception e )
			{
				throw new SpimDataIOException( e );
			}

			final Element docRoot = doc.getRootElement();

			if ( docRoot.getName() != SPIMDATA_TAG )
				throw new RuntimeException( "expected <" + SPIMDATA_TAG + "> root element. wrong file?" );

			final SpimData2 data = io.fromXml( docRoot, xmlURI );

			return data;
		}
		else
		{
			throw new RuntimeException( "Unsupported URI: " + xmlURI );
		}
	}

	public static boolean setNumFetcherThreads( final BasicImgLoader loader, final int threads )
	{
		if ( ViewerImgLoader.class.isInstance( loader ) )
		{
			( (ViewerImgLoader) loader ).setNumFetcherThreads( threads );
			return true;
		}
		else
		{
			return false;
		}
	}

	public static boolean prefetch( final BasicImgLoader loader, final int threads )
	{
		if ( N5ImageLoader.class.isInstance( loader ) )
		{
			( ( N5ImageLoader ) loader ).prefetch( threads );
			return true;
		}
		else if ( SplitViewerImgLoader.class.isInstance( loader ) )
		{
			if ( ( ( SplitViewerImgLoader ) loader ).prefetch( threads ) != null )
				return true;
			else
				return false;
		}
		else
			return false;
	}

	/**
	 * This is an abstraction that is only required because the Google cloud and AWS KeyValueAccesses behave differently
	 *
	 * @param uri - the URI for which to create the relative path
	 * @return the relative path
	 * @throws URISyntaxException
	 */
	public static String getRelativeCloudPath( final URI uri ) throws URISyntaxException
	{
		if ( URITools.isGC( uri ) )
			return new URI( null, null, uri.getPath(), null ).toString();
		else if ( URITools.isS3( uri ) )
			return new URI( uri.getScheme(), uri.getHost(), uri.getPath(), null ).toString(); // TODO: this is a bug
		else
		{
			if ( uri.getScheme() != null )
				throw new RuntimeException( "Unsupported uri scheme: " + uri.getScheme() + " in '" + uri + "'." );
			else
				throw new RuntimeException( "Cannot get a relative cloud path for a relative path '" + uri + "'." );
		}
	}

	public static BufferedReader openFileReadCloudReader( final KeyValueAccess kva, final URI uri ) throws IOException
	{
		return new BufferedReader(new InputStreamReader( openFileReadCloudStream( kva, uri )));
	}

	public static InputStream openFileReadCloudStream( final KeyValueAccess kva, final URI uri ) throws IOException
	{
		final String relativePath;

		try
		{
			relativePath = getRelativeCloudPath( uri );
		}
		catch (URISyntaxException e)
		{
			throw new IOException( e.getMessage() );
		}

		return kva.lockForReading( relativePath ).newInputStream();
	}

	public static PrintWriter openFileWriteCloudWriter( final KeyValueAccess kva, final URI uri ) throws IOException
	{
		return new PrintWriter( openFileWriteCloudStream( kva, uri ) );
	}

	public static OutputStream openFileWriteCloudStream( final KeyValueAccess kva, final URI uri ) throws IOException
	{
		final String relativePath;

		try
		{
			relativePath = getRelativeCloudPath( uri );
		}
		catch (URISyntaxException e)
		{
			throw new IOException( e.getMessage() );
		}

		return kva.lockForWriting( relativePath ).newOutputStream();
	}

	/**
	 * Note: it is up to you to create the correct relative paths using getRelativeCloudPath()
	 *
	 * @param kva
	 * @param relativeSrc
	 * @param relativeDst
	 * @throws IOException
	 */
	public static void copy( final KeyValueAccess kva, final String relativeSrc, final String relativeDst ) throws IOException
	{
		final InputStream is = kva.lockForReading( relativeSrc ).newInputStream();
		final OutputStream os = kva.lockForWriting( relativeDst ).newOutputStream();

		final byte[] buffer = new byte[32768];
		int len;
		while ((len = is.read(buffer)) != -1)
			os.write(buffer, 0, len);

		is.close();
		os.close();
	}

	public static boolean isKnownScheme( URI uri )
	{
		return isFile( uri ) || isS3( uri ) || isGC( uri );
	}

	public static boolean isGC( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;
		if ( !hasScheme )
			return false;
		if ( GoogleCloudUtils.GS_SCHEME.asPredicate().test( scheme ) )
			return true;
		return uri.getHost() != null && HTTPS_SCHEME.asPredicate().test( scheme ) && GoogleCloudUtils.GS_HOST.asPredicate().test( uri.getHost() );
	}

	public static boolean isS3( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;
		if ( !hasScheme )
			return false;
		if ( AmazonS3Utils.S3_SCHEME.asPredicate().test( scheme ) )
			return true;
		return uri.getHost() != null && HTTPS_SCHEME.asPredicate().test( scheme );
	}

	public static boolean isFile( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;

		if ( !hasScheme )
			return false;
		else 
			return FILE_SCHEME.asPredicate().test( scheme );
	}

	/**
	 * @param uriString - if relative we assume it's a local path and file:/ scheme will be added
	 * @return the URI of the String
	 */
	public static URI toURI( final String uriString )
	{
		URI uri;

		try
		{
			uri = new URI( uriString );
		}
		catch (URISyntaxException e)
		{
			// e.g. a space was in there, which is allowed for filepaths, but not other resources (must be %20)
			uri = null;
		}

		try
		{
			// maybe it works if we assume it is a file
			if ( uri == null )
				uri = new File( uriString ).toURI();

			if ( !uri.isAbsolute() )
				uri = new URI( "file", null, uriString, null );

			return uri;
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
			throw new RuntimeException( "URI couldn't be created from '" + uriString + "'. stopping: " + e );
		}
	}

	/**
	 * 
	 * @param uri
	 * @return a String representation of a URI, if it starts with file:/ it will be removed
	 */
	public static String fromURI( final URI uri )
	{
		final String scheme = uri.getScheme();

		if ( scheme == null )
			throw new RuntimeException( "URI '" + uri + "' has no scheme. stopping." );

		if ( FILE_SCHEME.asPredicate().test( uri.getScheme() ) )
		{
			try
			{
				return new File( uri ).toString();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				throw new RuntimeException( "Error converting file-URI '" + uri + "' to a path. stopping." );
			}
		}
		else
		{
			return uri.toString();
		}
	}

	public static String getFileName( final URI uri )
	{
		return Paths.get( uri.getPath() ).getFileName().toString();
	}

	public static String appendName( final URI uri, final String name )
	{
		return uri.toString() + ( uri.toString().endsWith( "/" ) ? "" : "/") + name;
	}

	public static URI xmlFilenameToFullPath( final AbstractSpimData<?> data, final String xmlFileName )
	{
		return toURI( appendName( data.getBasePathURI(), xmlFileName ) );
	}

	public static void copyFile( final File inputFile, final File outputFile ) throws IOException
	{
		InputStream input = null;
		OutputStream output = null;
		
		try
		{
			input = new FileInputStream( inputFile );
			output = new FileOutputStream( outputFile );

			final byte[] buf = new byte[ 65536 ];
			int bytesRead;
			while ( ( bytesRead = input.read( buf ) ) > 0 )
				output.write( buf, 0, bytesRead );

		}
		finally
		{
			if ( input != null )
				input.close();
			if ( output != null )
				output.close();
		}
	}

	public static void minimalExampleTobiS3GS() throws URISyntaxException, IOException
	{
		URI gcURI = URITools.toURI( "gs://janelia-spark-test/I2K-test/dataset.xml" );
		URI s3URI = URITools.toURI( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" );

		// assemble scheme + bucket and location for Google Cloud
		URI gcBucket = new URI( gcURI.getScheme(), gcURI.getHost(), null, null );
		URI gcLocation = new URI( null, null, gcURI.getPath(), null );

		System.out.println( "Google cloud: Instantiating N5Reader to grab a key-value-access for: '" + gcBucket + "'" );
		System.out.println( "Google cloud: Lock for reading on: '" + gcLocation  + "'");

		final N5Reader gcN5 = instantiateN5Reader(StorageFormat.N5, gcBucket );
		final KeyValueAccess gcKVA = ((GsonKeyValueN5Reader)gcN5).getKeyValueAccess();
		gcKVA.lockForReading( gcLocation.toString()).newInputStream().skip( 1000 );

		// assemble scheme + bucket and location for AWS S3 (using full path for location, which should actually be relative, same as for google cloud above)
		URI s3Bucket = new URI( s3URI.getScheme(), s3URI.getHost(), null, null );
		URI s3Location = new URI( s3URI.getScheme(), s3URI.getHost(), s3URI.getPath(), null );

		System.out.println( "AWS: Instantiating N5Reader to grab a key-value-access for: '" + s3Bucket + "'" );
		System.out.println( "AWS: Lock for reading on: '" + s3Location  + "'");

		final N5Reader s3N5 = instantiateN5Reader(StorageFormat.N5, s3Bucket );
		final KeyValueAccess s3KVA = ((GsonKeyValueN5Reader)s3N5).getKeyValueAccess();
		s3KVA.lockForReading( s3Location.toString() ).newInputStream().skip( 1000 );

		// assemble relative location for AWS S3 (which fails)
		s3Location = new URI( null, null, s3URI.getPath(), null );
		System.out.println( "AWS: Lock for reading on: '" + s3Location  + "' (fails)");
		s3KVA.lockForReading( s3Location.toString() ).newInputStream().skip( 1000 );
	}

	public static void main( String[] args ) throws SpimDataException, IOException, URISyntaxException
	{
		URI uri1 = URITools.toURI( "s3://aind-open-data/exaSPIM_708373_2024-04-02_19-49-38/SPIM.ome.zarr" );

		System.out.println( uri1.getHost() );
		System.out.println( uri1.getPath() );
		System.exit( 0 );

		minimalExampleTobiS3GS();

		System.exit( 0 );

		URI gcURI = URITools.toURI( "gs://janelia-spark-test/I2K-test/dataset.xml" );
		System.out.println( "isGC: " + isGC(gcURI) + " [" + gcURI + "]" );
		SpimData2 sdGC = loadSpimData(gcURI, new XmlIoSpimData2() );
		System.out.println( sdGC.getBasePathURI() + ", " + sdGC.getSequenceDescription().getAllTilesOrdered() );

		URI s3URI = URITools.toURI( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" );
		System.out.println( "isS3: " + isS3(s3URI) + " [" + s3URI + "]" );
		SpimData2 sdS3 = loadSpimData(s3URI, new XmlIoSpimData2() );
		System.out.println( sdS3.getBasePathURI() + ", " + sdS3.getSequenceDescription().getAllTilesOrdered() );

		System.exit( 0 );

		// Fails:
		//URI uri = new URI( "/Users/preibischs/SparkTest/IP raw/spim_TL18_Angle0.tif" );
		URI uri = new File( "/Users/preibischs/SparkTest/IP raw/spim_TL18_Angle0.tif" ).toURI();

		System.out.println( new File( uri ) );
		System.out.println( URITools.fromURI( uri ) );

		System.out.println( uri );

		String file = "/home/preibisch/test.xml";
		String s3 = "s3://preibisch/test.xml";

		System.out.println( toURI( file ) );
		System.out.println( toURI( s3 ) );

		System.out.println();

		System.out.println( fromURI( toURI( file ) ) );
		System.out.println( fromURI( toURI( s3 ) ) );

		System.out.println();

		KeyValueAccess kva = getKeyValueAccess( URI.create( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" ) );

		try
		{
			BufferedReader reader = openFileReadCloudReader(kva, URI.create( "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" ) );
			reader.lines().forEach( s -> System.out.println( s ) );
			reader.close();

			/*
			String path = pb.protocol + pb.bucket + "/" + pb.rootDir + "/" + "test_" + System.currentTimeMillis() + ".txt";

			final PrintWriter pw = openFileWriteCloud( kva, path );
			pw.println( "hallo " + System.currentTimeMillis() );
			pw.close();
			*/
		}
		catch ( Exception e )
		{
			throw new SpimDataException( "Could not save xml '" + "': " + e );
		}	
	}
}
