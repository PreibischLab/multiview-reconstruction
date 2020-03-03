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
	String[] angles; // directories

	int numChannels;
	String[] channels;
	SimViewChannel[] metaDataChannels;

	double xStep = 0.4125, yStep = 0.4125;
	double zStep = 1;
	int numCameras = 0;
	long[] stackSize = null;

	/**
	 * assigns global metadata
	 * @return true if metadata is consistent, otherwise false
	 */
	public boolean assignGlobalValues()
	{
		this.zStep = metaDataChannels[ 0 ].z_step;

		for ( int i = 1; i < numChannels; ++i )
			if ( metaDataChannels[ i ].z_step != this.zStep )
			{
				IOFunctions.println( "z-stepping inconsistent, " + this.zStep + "!= " + metaDataChannels[ i ].z_step + ".");
				return false;
			}

		this.numCameras = metaDataChannels[ 0 ].numCameras;

		for ( int i = 1; i < numChannels; ++i )
			if ( metaDataChannels[ i ].numCameras != this.numCameras )
			{
				IOFunctions.println( "numCameras inconsistent, " + this.numCameras + "!= " + metaDataChannels[ i ].numCameras + ".");
				return false;
			}

		this.stackSize = new long[]{ metaDataChannels[ 0 ].stackSizes[ 0 ][ 0 ], metaDataChannels[ 0 ].stackSizes[ 0 ][ 1 ], metaDataChannels[ 0 ].stackSizes[ 0 ][ 2 ] };

		for ( int i = 0; i < numChannels; ++i )
			for ( int c = 0; c < metaDataChannels[ i ].stackSizes.length; ++c )
				for ( int d = 0; d < 3; ++d  )
					if ( stackSize[ d ] != metaDataChannels[ i ].stackSizes[ c ][ d ] )
					{
						IOFunctions.println( "stackSize inconsistent, " + Util.printCoordinates( this.stackSize ) + "!= " + Util.printCoordinates(  metaDataChannels[ i ].stackSizes[ c ] ) + ".");
						return false;						
					}

		return true;
	}

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

		HashMap<String , String > metadataHash;
	}

	public static SimViewChannel parseSimViewXML( final File file ) throws JDOMException, IOException
	{
		final SimViewChannel metaData = new SimViewChannel();
		metaData.metadataHash = new HashMap<String, String>();

		final SAXBuilder sax = new SAXBuilder();
		Document doc = sax.build( file );

		final Element root = doc.getRootElement();
		
		final List< Element > children = root.getChildren();
		
		for ( final Element e : children )
			metaData.metadataHash.put( e.getAttributes().get(0).getName(), e.getAttributes().get(0).getValue() );

		// parse common metadata
		String dimensions = metaData.metadataHash.getOrDefault( "dimensions", null );

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

		metaData.specimen_name = metaData.metadataHash.getOrDefault( "specimen_name", null );
		metaData.timestamp = metaData.metadataHash.getOrDefault( "timestamp", null );
		metaData.specimen_XYZT = metaData.metadataHash.getOrDefault( "specimen_XYZT", null );
		if ( metaData.metadataHash.containsKey( "angle" ) )
			metaData.angle = Double.parseDouble( metaData.metadataHash.get( "angle" ) );
		if ( metaData.metadataHash.containsKey( "wavelength" ) )
			metaData.wavelength = Integer.parseInt( metaData.metadataHash.get( "wavelength" ) );
		if ( metaData.metadataHash.containsKey( "z_step" ) )
			metaData.z_step = Double.parseDouble( metaData.metadataHash.get( "z_step" ) );

		IOFunctions.println( "specimen_name: " + metaData.specimen_name );
		IOFunctions.println( "timestamp: " + metaData.timestamp );
		IOFunctions.println( "angle: " + metaData.angle );
		IOFunctions.println( "wavelength: " + metaData.wavelength );
		IOFunctions.println( "z_step: " + metaData.z_step );
		IOFunctions.println( "specimen_XYZT: " + metaData.specimen_XYZT );

		return metaData;
	}

}
