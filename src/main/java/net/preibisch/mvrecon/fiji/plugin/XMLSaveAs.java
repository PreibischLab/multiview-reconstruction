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
package net.preibisch.mvrecon.fiji.plugin;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.bigdataviewer.n5.N5CloudImageLoader;

import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.n5.N5ImageLoader;
import fiji.util.gui.GenericDialogPlus;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ImgLoader;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import util.URITools;

public class XMLSaveAs implements PlugIn
{
	@Override
	public void run( String arg )
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Save XML as ...", "", false, false, false, false, false ) )
			return;

		final SpimData2 data = result.getData();
		final XmlIoSpimData2 io = result.getIO();

		final URI newXMLPath = saveAs( data, result.getXMLFileName() );

		if ( newXMLPath != null )
		{
			io.save( data, newXMLPath );

			IOFunctions.println( "Done." );
		}
	}

	public static URI saveAs( final SpimData2 data, final String suggestedFileName )
	{
		final ImgLoader imgLoader = data.getSequenceDescription().getImgLoader();

		if ( !N5ImageLoader.class.isInstance( imgLoader ) && !Hdf5ImageLoader.class.isInstance( imgLoader ) )
		{
			IOFunctions.println( "Saving the XML in a different location than the image data currently only works when it is re-saved as HDF5/N5/ZARR -- Consider resaving. Stopping." );
			return null;
		}

		final GenericDialogPlus gd = new GenericDialogPlus( "Save BigStitcher project as ..." );
		gd.addFileField( "Dataset location (local or cloud)", URITools.appendName( data.getBasePathURI(), suggestedFileName), 120 );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final String uriString = gd.getNextString();
		final URI newXMLPath, newBaseDir;

		newXMLPath = URITools.toURI( uriString );

		if ( !URITools.isKnownScheme( newXMLPath ) )
		{
			IOFunctions.println( "The scheme of the XML path you selected '" + newXMLPath + "' is unknown." );
			return null;
		}

		newBaseDir = URITools.getParent( newXMLPath );

		IOFunctions.println( "New XML: " + newXMLPath );
		IOFunctions.println( "New base path: " + newBaseDir );

		if ( N5CloudImageLoader.class.isInstance( imgLoader ) )
		{
			final URI n5URI = ((N5CloudImageLoader)imgLoader).getN5URI();

			IOFunctions.println( "Path of cloud N5 (stays in old location): " + n5URI );
			data.getSequenceDescription().setImgLoader( new N5CloudImageLoader( null, n5URI, data.getSequenceDescription() ) );
		}
		else if ( N5ImageLoader.class.isInstance( imgLoader ) )
		{
			final URI n5URI = ((N5ImageLoader)imgLoader).getN5URI();

			IOFunctions.println( "Path of N5 (stays in old location): " + n5URI );
			data.getSequenceDescription().setImgLoader( new N5ImageLoader( new File( URITools.fromURI( n5URI ) ), data.getSequenceDescription() ) );
		}
		else if ( Hdf5ImageLoader.class.isInstance( imgLoader ) )
		{
			final Hdf5ImageLoader h5ImgLoader = (Hdf5ImageLoader)imgLoader;
			final File h5File = h5ImgLoader.getHdf5File();

			IOFunctions.println( "HDF5 file (stays in old location): " + h5File );
			data.getSequenceDescription().setImgLoader( new Hdf5ImageLoader(h5File, h5ImgLoader.getPartitions(), data.getSequenceDescription()) );
		}

		data.setBasePathURI( newBaseDir );

		// make sure interestpoints are saved to the new location as well
		for ( final ViewInterestPointLists vipl : data.getViewInterestPoints().getViewInterestPoints().values() )
			vipl.getHashMap().values().forEach( ipl ->
			{
				ipl.getInterestPointsCopy();
				ipl.getCorrespondingInterestPointsCopy();
				ipl.setBaseDir( newBaseDir ); // also sets 'isModified' flags
			});

		LoadParseQueryXML.defaultXMLURI = newXMLPath.toString();

		return newXMLPath;
	}
}
