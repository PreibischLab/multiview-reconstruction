package net.preibisch.mvrecon.fiji.plugin;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

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
			IOFunctions.println( "Saving the XML in a different location than the image data currently only works when it is re-saved as HDF5/N5/ZARR. Stopping." );
			return null;
		}

		final GenericDialogPlus gd = new GenericDialogPlus( "Save BigStitcher project as ..." );
		gd.addFileField( "Dataset location (local or cloud)", URITools.appendName( data.getBasePathURI(), suggestedFileName), 120 );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final String uriString = gd.getNextString();
		final URI newXMLPath, newBaseDir;

		try
		{
			newXMLPath = new URI( uriString );
		}
		catch ( URISyntaxException ex )
		{
			IOFunctions.println( "Could not convert provided XML path into a URI: " + ex );
			return null;
		}

		if ( !URITools.isKnownScheme( newXMLPath ) )
		{
			IOFunctions.println( "The scheme of the XML path you selected '" + newXMLPath + "' is unknown." );
			return null;
		}

		newBaseDir = URITools.getParent( newXMLPath );

		IOFunctions.println( "New XML: " + newXMLPath );
		IOFunctions.println( "New base path: " + newBaseDir );

		if ( N5ImageLoader.class.isInstance( imgLoader ) )
		{
			final URI n5URI = ((N5ImageLoader)imgLoader).getN5URI();

			IOFunctions.println( "Path of N5 (stays in old location): " + n5URI );
			data.getSequenceDescription().setImgLoader( new N5ImageLoader( n5URI, data.getSequenceDescription() ) );
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
			vipl.getHashMap().values().forEach( ipl -> ipl.setBaseDir( newBaseDir ) );

		LoadParseQueryXML.defaultXMLURI = newXMLPath.toString();

		return newXMLPath;
	}
}
