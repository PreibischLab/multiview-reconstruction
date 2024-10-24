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
import java.nio.file.Path;
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
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import bdv.ViewerImgLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.SpimDataIOException;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class URITools
{
	private final static Pattern HTTPS_SCHEME = Pattern.compile( "http(s)?", Pattern.CASE_INSENSITIVE );
	private final static Pattern FILE_SCHEME = Pattern.compile( "file", Pattern.CASE_INSENSITIVE );

	public static int cloudThreads = 256;

	public static boolean useS3CredentialsWrite = true;
	public static boolean useS3CredentialsRead = true;

	public static class ParsedBucket
	{
		public String protocol;
		public String bucket;
		public String rootDir;
		public String file;

		@Override
		public String toString()
		{
			return "protocol: '" + protocol + "', bucket: '" + bucket + "', rootdir: '" + rootDir + "', file: '" + file + "'";
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
			final ParsedBucket pb;
			final KeyValueAccess kva;

			System.out.println( xmlURI );

			try
			{
				pb = URITools.parseCloudLink( xmlURI.toString() );
				kva = URITools.getKeyValueAccessForBucket( pb );
			}
			catch ( Exception e )
			{
				throw new SpimDataException( "Could not parse cloud link and setup KeyValueAccess for '" + xmlURI + "': " + e );
			}

			System.out.println( pb );

			// fist make a copy of the XML and save it to not loose it
			try
			{
				final String xmlFile;

				if ( URITools.isS3( xmlURI ) )
					xmlFile = pb.protocol + pb.bucket + "/" + pb.rootDir + "/" + pb.file;
				else
					xmlFile = pb.rootDir + "/" + pb.file;

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
				final Document doc = new Document( io.toXml( data, getParent( xmlURI ) ) );
				final XMLOutputter xout = new XMLOutputter( Format.getPrettyFormat() );
				final String xmlString = xout.outputString( doc );

				String xmlPath;
				if ( URITools.isS3( xmlURI ) )
					xmlPath = pb.protocol + pb.bucket + "/" + pb.rootDir + "/" + pb.file;
				else
					xmlPath = pb.rootDir + "/" + pb.file;

				final PrintWriter pw = openFileWriteCloud( kva, xmlPath );
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
		if ( URITools.isFile( uri ) )
		{
			if ( format.equals( StorageFormat.N5 ))
				return new N5FSWriter( URITools.fromURI( uri ) );
			else if ( format.equals( StorageFormat.ZARR ))
				return new N5ZarrWriter( URITools.fromURI( uri ) );
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
				N5Factory factory = new N5Factory();
				factory.s3UseCredentials();
				n5w = factory.openWriter( format, uri );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous ..." );

				n5w = new N5Factory().openWriter( format, uri );
			}

			return n5w;
			//return new N5Factory().openWriter( format, uri ); // cloud support, avoid dependency hell if it is a local file
		}
	}

	public static N5Reader instantiateN5Reader( final StorageFormat format, final URI uri )
	{
		if ( URITools.isFile( uri ) )
		{
			if ( format.equals( StorageFormat.N5 ))
				return new N5FSReader( URITools.fromURI( uri ) );
			else if ( format.equals( StorageFormat.ZARR ))
				return new N5ZarrReader( URITools.fromURI( uri ) );
			else
				throw new RuntimeException( "Format: " + format + " not supported." );
		}
		else
		{
			N5Reader n5r;

			try
			{
				//System.out.println( "Trying reading with credentials ..." );
				N5Factory factory = new N5Factory();
				factory.s3UseCredentials();
				n5r = factory.openReader( format, uri );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous ..." );
				n5r = new N5Factory().openReader( format, uri );
			}

			return n5r;
			//return new N5Factory().openReader( format, uri ); // cloud support, avoid dependency hell if it is a local file
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

			try
			{
				//System.out.println( "Trying reading with credentials ..." );
				N5Factory factory = new N5Factory();
				factory.s3UseCredentials();
				n5r = factory.openReader( uri.toString() );
			}
			catch ( Exception e )
			{
				System.out.println( "With credentials failed; trying anonymous ..." );
				n5r = new N5Factory().openReader( uri.toString() );
			}

			return n5r;
			//return new N5Factory().openReader( uri.toString() ); // cloud support, avoid dependency hell if it is a local file
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

			try
			{
				//System.out.println( "Trying writing with credentials ..." );
				N5Factory factory = new N5Factory();
				factory.s3UseCredentials();
				n5w = factory.openWriter( uri.toString() );
			}
			catch ( Exception e )
			{
				System.out.println( "Writing with credentials failed; trying anonymous writing ..." );
				n5w = new N5Factory().openWriter( uri.toString() );
			}

			return n5w;

			//return new N5Factory().openWriter( uri.toString() ); // cloud support, avoid dependency hell if it is a local file
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
			//super.load(null, xmlURI); // how do I use this?

			final ParsedBucket pb = URITools.parseCloudLink( xmlURI.toString() );
			final KeyValueAccess kva = URITools.getKeyValueAccessForBucket( pb );

			final SAXBuilder sax = new SAXBuilder();
			Document doc;
			try
			{
				final InputStream is;

				if ( URITools.isS3( xmlURI ) )
					is = kva.lockForReading( pb.protocol + pb.bucket + "/" + pb.rootDir + "/" + pb.file ).newInputStream();
				else // google cloud
					is = kva.lockForReading( pb.rootDir + "/" + pb.file ).newInputStream();

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

			// more threads for cloud-based fetching
			( (ViewerImgLoader) data.getSequenceDescription().getImgLoader() ).setNumFetcherThreads( cloudThreads );

			return data;
		}
		else
		{
			throw new RuntimeException( "Unsupported URI: " + xmlURI );
		}
	}

	public static KeyValueAccess getKeyValueAccessForBucket( final ParsedBucket pb )
	{
		// we use a reader so it does not create an attributes.json; the KeyValueAccess is able to write anyways
		final N5Reader n5r = instantiateN5Reader(StorageFormat.N5, URI.create( pb.protocol + pb.bucket ) );//new N5Factory().openReader( StorageFormat.N5, pb.protocol + pb.bucket );
		final KeyValueAccess kva = ((GsonKeyValueN5Reader)n5r).getKeyValueAccess();

		return kva;
	}

	public static ParsedBucket parseCloudLink( final String uri )
	{
		//System.out.println( "Parsing link path for '" + uri + "':" );

		final ParsedBucket pb = new ParsedBucket();

		final File f = new File( uri );
		String parent = f.getParent().replace( "//", "/" ); // new File cuts // already, but just to make sure
		parent = parent.replace(":/", "://" );
		pb.protocol = parent.substring( 0, parent.indexOf( "://" ) + 3 );
		parent = parent.substring( parent.indexOf( "://" ) + 3, parent.length() );

		if (parent.contains( "/" ) )
		{
			// there is an extra path
			pb.bucket = parent.substring(0,parent.indexOf( "/" ) );
			pb.rootDir = parent.substring(parent.indexOf( "/" ) + 1, parent.length() );
		}
		else
		{
			pb.bucket = parent;
			pb.rootDir = "/";
		}

		pb.file = f.getName();

		//System.out.println( "protocol: '" + pb.protocol + "'" );
		//System.out.println( "bucket: '" + pb.bucket + "'" );
		//System.out.println( "root dir: '" + pb.rootDir + "'" );
		//System.out.println( "xmlFile: '" + pb.file + "'" );

		return pb;
	}

	public static BufferedReader openFileReadCloud( final KeyValueAccess kva, final String file ) throws IOException
	{
		final InputStream is = kva.lockForReading( file ).newInputStream();
		return new BufferedReader(new InputStreamReader(is));
	}

	public static PrintWriter openFileWriteCloud( final KeyValueAccess kva, final String file ) throws IOException
	{
		final OutputStream os = kva.lockForWriting( file ).newOutputStream();
		return new PrintWriter( os );
	}

	public static void copy( final KeyValueAccess kva, final String src, final String dst ) throws IOException
	{
		final InputStream is = kva.lockForReading( src ).newInputStream();
		final OutputStream os = kva.lockForWriting( dst ).newOutputStream();

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

	/*
	public static File toFile( final URI uri )
	{
		if ( !isFile( uri ) )
			throw new RuntimeException( "Cannot make a java.io.File from '" + uri + "'" );

		// otherwise triggers Exception in thread "main" java.lang.IllegalArgumentException: URI is not absolute
		if ( !uri.toString().toLowerCase().startsWith( "file:") )
			return new File( uri.toString() );
		else
			return new File( uri );
	}

	public static String removeFilePrefix( URI uri )
	{
		final String scheme = uri.getScheme();
		final boolean hasScheme = scheme != null;

		if ( hasScheme && FILE_SCHEME.asPredicate().test( scheme ) )
			return uri.toString().substring( 5, uri.toString().length() ); // cut off 'file:'
		else
			return uri.toString();
	}
	*/

	public static URI getParent( final URI uri )
	{
		return uri.getPath().endsWith("/") ? uri.resolve("..") : uri.resolve(".");
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

	public static void main( String[] args ) throws SpimDataException, IOException, URISyntaxException
	{
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

		ParsedBucket pb = URITools.parseCloudLink( "s3://janelia-bigstitcher-spark/Stitching-test/dataset.xml" );
		KeyValueAccess kva = URITools.getKeyValueAccessForBucket( pb );

		System.out.println( pb );

		try
		{
			BufferedReader reader = openFileReadCloud(kva, "s3://janelia-bigstitcher-spark/Stitching/dataset.xml" );
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
