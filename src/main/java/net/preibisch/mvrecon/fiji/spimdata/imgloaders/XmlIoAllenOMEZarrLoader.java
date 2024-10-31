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
import java.util.Map.Entry;

import org.jdom2.Element;

import mpicbg.spim.data.XmlHelpers;
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
		final Element imgLoaderElement = new Element( "ImageLoader" );
		imgLoaderElement.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, "bdv.multimg.zarr" );
		imgLoaderElement.setAttribute( "version", "1.0" );

		if ( URITools.isS3( imgLoader.getN5URI() ) || URITools.isGC( imgLoader.getN5URI() ) )
		{
			final Element bucketElement = new Element("s3bucket");
			bucketElement.addContent( imgLoader.getBucket() );
			imgLoaderElement.addContent(bucketElement);
			
			final Element zarrElement = new Element("zarr");
			zarrElement.setAttribute("type", "absolute");
			zarrElement.addContent( imgLoader.getFolder() );
			imgLoaderElement.addContent( zarrElement );
		}
		else
		{
			imgLoaderElement.addContent( XmlHelpers.pathElement( "zarr", new File( imgLoader.getN5URI() ), null));
		}

		final Element zgroupsElement = new Element( "zgroups" );

		for ( final Entry<ViewId, String > entry : imgLoader.getViewIdToPath().entrySet() )
		{
			final Element zgroupElement = new Element("zgroup");
			zgroupElement.setAttribute( "setup", String.valueOf( entry.getKey().getViewSetupId() ) );
			zgroupElement.setAttribute( "timepoint", String.valueOf( entry.getKey().getTimePointId() ) );

			final Element pathElement = new Element( "path" );
			pathElement.addContent( entry.getValue() );
			zgroupElement.addContent( pathElement );

			zgroupsElement.addContent( zgroupElement );
		}

		return imgLoaderElement;
	}

	@Override
	public AllenOMEZarrLoader fromXml( final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		return fromXml( elem, basePath.toURI(), sequenceDescription );
	}

	@Override
	public AllenOMEZarrLoader fromXml( final Element elem, final URI basePathURI, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		final HashMap<ViewId, String> zgroups = new HashMap<>();

		final Element s3Bucket = elem.getChild( "s3bucket" );
		final URI uri;
		String bucket, folder;

		if (s3Bucket == null)
		{
			uri = XmlHelpers.loadPathURI( elem, "zarr", basePathURI );

			bucket = null;
			folder = null;
		}
		else
		{
			// `File` class should not be used for uri manipulation as it replaces slashes
			// with backslashes on Windows
			bucket = s3Bucket.getText();

			folder = elem.getChildText( "zarr" );

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
			}
		}

		try
		{
			System.out.println( "Opening N5 Zarr reader for '" + uri + "'" );

			return new AllenOMEZarrLoader( uri, bucket, folder, sequenceDescription, zgroups );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
