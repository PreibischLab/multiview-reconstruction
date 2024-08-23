package util;

import java.net.URI;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudUtils;
import org.janelia.saalfeldlab.n5.s3.AmazonS3Utils;

import mpicbg.spim.data.generic.AbstractSpimData;

public class URITools
{
	private final static Pattern HTTPS_SCHEME = Pattern.compile( "http(s)?", Pattern.CASE_INSENSITIVE );
	private final static Pattern FILE_SCHEME = Pattern.compile( "file", Pattern.CASE_INSENSITIVE );

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
		return !hasScheme || FILE_SCHEME.asPredicate().test( scheme );
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

	public static URI xmlFilenameToFullPath( final AbstractSpimData<?> data, final String xmlFileName )
	{
		return URI.create( data.getBasePathURI().toString() + ( data.getBasePathURI().toString().endsWith( "/" ) ? "" : "/") + xmlFileName );
	}

}
