package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

/*
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2024 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;
import org.jdom2.Element;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import util.URITools;

@ImgLoaderIo( format = "bdv.multimg.zarr", type = AllenOMEZarrLoader.class )
public class XmlIoAllenOMEZarrLoader implements XmlIoBasicImgLoader< AllenOMEZarrLoader >
{
	public static boolean PREFER_URI_FOR_LOCAL_FILES = true;

	@Override
	public Element toXml( final AllenOMEZarrLoader imgLoader, final File basePath )
	{
		return toXml( imgLoader, basePath == null ? null : basePath.toURI() );
	}

	@Override
	public Element toXml( final AllenOMEZarrLoader imgLoader, final URI basePathURI )
	{
		final Element elem = new Element( "ImageLoader" );
		elem.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, "bdv.multimg.zarr" );
		elem.setAttribute( "version", "1.0" );

		// TODO: implement
		/*
		final File n5File = getLocalFile( imgLoader.getN5URI() );
		if ( !PREFER_URI_FOR_LOCAL_FILES && n5File != null )
		{
			final File basePath = basePathURI == null ? null : new File( basePathURI );
			elem.addContent( XmlHelpers.pathElement( "n5", n5File, basePath ) );
		}
		else
		{
			elem.addContent( XmlHelpers.pathElementURI( "n5", imgLoader.getN5URI(), basePathURI ) );
		}*/
		return elem;
	}

	@Override
	public AllenOMEZarrLoader fromXml( final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		return fromXml( elem, basePath.toURI(), sequenceDescription );
	}

	@Override
	public AllenOMEZarrLoader fromXml( final Element elem, final URI basePathURI, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
//		final String version = elem.getAttributeValue( "version" );
//		final URI uri = XmlHelpers.loadPathURI( elem, "n5", basePathURI );

		final HashMap<ViewId, String> zgroups = new HashMap<>();

		final Element s3Bucket = elem.getChild( "s3bucket" );
		final URI uri;

		if (s3Bucket == null)
		{
			// TODO: we do not support that here for now
			throw new RuntimeException( "Only S3 bucket storage is supported for now." );
		}
		else
		{
			// `File` class should not be used for uri manipulation as it replaces slashes
			// with backslashes on Windows
			final String bucket = s3Bucket.getText();
			String folder = elem.getChildText( "zarr" );

			if ( !folder.endsWith( "/" ) )
				folder = folder + "/";

			if ( !folder.startsWith( "/" ) )
				folder = "/" + folder;

			try
			{
				uri = new URI( "s3", bucket, folder, null );
			}
			catch (URISyntaxException e)
			{
				e.printStackTrace();
				throw new RuntimeException( "Could not instantiate N5 reader for S3 bucket '" + bucket + "'." );
			}

			final Element zgroupsElem = elem.getChild( "zgroups" );
			for (final Element c : zgroupsElem.getChildren( "zgroup" ))
			{
				final int timepointId = Integer.parseInt( c.getAttributeValue( "timepoint" ) );
				final int setupId = Integer.parseInt( c.getAttributeValue( "setup" ) );
				final String path = c.getChild( "path" ).getText();
				zgroups.put( new ViewId( timepointId, setupId ), path );

				//System.out.println( Group.pvid( new ViewId( timepointId, setupId ) )+ ": " + (folder+path) );
			}
			//System.exit( 0 );
		}

		try
		{
			System.out.println( "Opening N5 Zarr reader for '" + uri + "'" );

			return new AllenOMEZarrLoader( uri, sequenceDescription, zgroups );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
