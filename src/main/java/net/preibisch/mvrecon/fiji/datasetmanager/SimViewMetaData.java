package net.preibisch.mvrecon.fiji.datasetmanager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;

public class SimViewMetaData
{
	File rootDir, expDir;
	String[] baseXMLs; // relative to expDir

	int numTimePoints;
	String[] timePoints;

	int numAngles;
	String[] angles;

	int numChannels;
	String[] channels;
	SimViewChannel[] metaDataChannels;

	public static class SimViewChannel
	{
		int wavelength;
		double z_step = 1;

		int numCameras;
		int[][] stackSizes;

		// non-essential metadata
		String specimen_name;
		String timestamp;
		String specimen_XYZT;
		double angle = 0;
	}

	public static SimViewChannel parseSimViewXML( final File file ) throws JDOMException, IOException
	{
		final SimViewChannel metaData = new SimViewChannel();
		final HashMap<String , String > metadataHash = new HashMap<String, String>();

		final SAXBuilder sax = new SAXBuilder();
		Document doc = sax.build( file );

		final Element root = doc.getRootElement();
		
		final List< Element > children = root.getChildren();
		
		for ( final Element e : children )
			metadataHash.put( e.getAttributes().get(0).getName(), e.getAttributes().get(0).getValue() );

		// parse common metadata
		String dimensions = metadataHash.getOrDefault( "dimensions", null );

		if ( dimensions == null )
		{
			IOFunctions.println( "dimensions tag not defined in XML " + file.getAbsolutePath() );
			IOFunctions.println( "cannot load dimensions & camera settings. quitting." );
		}
		else
		{
			//928x1000x21 or 2048x2048x131,2048x2048x131
			//defines number of cameras used
			String[] dims = dimensions.split( "," );
			metaData.numCameras = dims.length;
			
			IOFunctions.println( "num cameras: " + metaData.numCameras );
			
			metaData.stackSizes = new int[ metaData.numCameras ][ 3 ];
			
			for ( int cam = 0; cam < metaData.numCameras; ++cam )
			{
				String[] perDim = dims[ cam ].split( "x" );
				metaData.stackSizes[ cam ][ 0 ] = Integer.parseInt( perDim[ 0 ] );
				metaData.stackSizes[ cam ][ 1 ] = Integer.parseInt( perDim[ 1 ] );
				metaData.stackSizes[ cam ][ 2 ] = Integer.parseInt( perDim[ 2 ] );
				
				IOFunctions.println( "camera " + cam + ": " + Util.printCoordinates( metaData.stackSizes[ cam ] ) );
			}
		}

		metaData.specimen_name = metadataHash.getOrDefault( "specimen_name", null );
		metaData.timestamp = metadataHash.getOrDefault( "timestamp", null );
		metaData.specimen_XYZT = metadataHash.getOrDefault( "specimen_XYZT", null );
		if ( metadataHash.containsKey( "angle" ) )
			metaData.angle = Double.parseDouble( metadataHash.get( "angle" ) );
		if ( metadataHash.containsKey( "wavelength" ) )
			metaData.wavelength = Integer.parseInt( metadataHash.get( "wavelength" ) );
		if ( metadataHash.containsKey( "z_step" ) )
			metaData.z_step = Double.parseDouble( metadataHash.get( "z_step" ) );

		IOFunctions.println( "specimen_name: " + metaData.specimen_name );
		IOFunctions.println( "timestamp: " + metaData.timestamp );
		IOFunctions.println( "angle: " + metaData.angle );
		IOFunctions.println( "wavelength: " + metaData.wavelength );
		IOFunctions.println( "z_step: " + metaData.z_step );
		IOFunctions.println( "specimen_XYZT: " + metaData.specimen_XYZT );

		return metaData;
	}

}
