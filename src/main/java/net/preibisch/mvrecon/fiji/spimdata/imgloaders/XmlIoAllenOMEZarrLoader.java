package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

/*
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jdom2.Attribute;
import org.jdom2.Element;

import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AllenOMEZarrLoader.OMEZARREntry;

@ImgLoaderIo( format = "bdv.multimg.zarr", type = AllenOMEZarrLoader.class )
public class XmlIoAllenOMEZarrLoader implements XmlIoBasicImgLoader< AllenOMEZarrLoader >
{
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
		imgLoaderElement.setAttribute( "version", "3.0" );

		imgLoaderElement.addContent( XmlHelpers.pathElementURI( "zarr", imgLoader.getN5URI(), basePathURI ));

		final Element zgroupsElement = new Element( "zgroups" );

		for ( final Entry<ViewId, OMEZARREntry > entry : imgLoader.getViewIdToPath().entrySet() )
		{
			final Element zgroupElement = new Element("zgroup");
			zgroupElement.setAttribute( "setup", String.valueOf( entry.getKey().getViewSetupId() ) );
			zgroupElement.setAttribute( "tp", String.valueOf( entry.getKey().getTimePointId() ) );
			zgroupElement.setAttribute( "path", String.valueOf( entry.getValue().getPath() ) );
			if ( entry.getValue().getHigherDimensionIndicies() == null || entry.getValue().getHigherDimensionIndicies().length == 0 )
				zgroupElement.setAttribute( "indicies", "[]" );
			else
				zgroupElement.setAttribute( "indicies", XmlHelpers.intArrayElement( "tmp", entry.getValue().getHigherDimensionIndicies()).getText() );

			/*
			final Element pathElement = new Element( "path" );
			pathElement.addContent( entry.getValue() );
			zgroupElement.addContent( pathElement );
			*/

			zgroupsElement.addContent( zgroupElement );
		}

		imgLoaderElement.addContent( zgroupsElement );

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
		final Map<ViewId, OMEZARREntry> zgroups = new HashMap<>();

		final Attribute ver = elem.getAttribute( "version" );

		final String version;
		if ( ver != null )
			version = ver.getValue();
		else
			version = "";

		final URI uri;

		if ( version.equals( "1.0" ) )
		{
			final Element s3Bucket = elem.getChild( "s3bucket" );
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
				catch ( URISyntaxException e )
				{
					e.printStackTrace();
					throw new RuntimeException( "Could not instantiate OME-ZARR reader for S3 bucket '" + bucket + "'." );
				}
			}

			final Element zgroupsElem = elem.getChild( "zgroups" );
			for ( final Element c : zgroupsElem.getChildren( "zgroup" ) )
			{
				final int timepointId = Integer.parseInt( c.getAttributeValue( "timepoint" ) );
				final int setupId = Integer.parseInt( c.getAttributeValue( "setup" ) );
				final String path = c.getChild( "path" ).getText();
				zgroups.put( new ViewId( timepointId, setupId ), new OMEZARREntry(path, null) );
			}
		}
		else
		{
			uri = XmlHelpers.loadPathURI( elem, "zarr", basePathURI );

			final Element zgroupsElem = elem.getChild( "zgroups" );
			for ( final Element c : zgroupsElem.getChildren( "zgroup" ) )
			{
				final int timepointId = Integer.parseInt( c.getAttributeValue( "tp" ) );
				final int setupId = Integer.parseInt( c.getAttributeValue( "setup" ) );
				final String path = c.getAttributeValue( "path" );
				int[] indicies;

				if ( version.equals( "3.0" ))
				{
					try
					{
						indicies = XmlHelpers.getIntArray( c, "indicies" );
					}
					catch ( Exception e )
					{
						indicies = null;
					}
					/*
					final String indiciesString = c.getAttributeValue( "indicies" );

					if ( indiciesString.equals( "[]" ) )
						indicies = null;
					else
						indicies = XmlHelpers.getIntArray( c, "indicies" );*/
				}
				else
				{
					indicies = null;
				}

				zgroups.put( new ViewId( timepointId, setupId ), new OMEZARREntry(path, indicies) );
			}
		}

		try
		{
			return new AllenOMEZarrLoader( uri, sequenceDescription, zgroups );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "ERROR: cannot instantiate OMEZarrLoader: " + e );
			return null;
		}
	}
}
