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
package net.preibisch.mvrecon.fiji.spimdata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.XmlIoBoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.XmlIoIntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.XmlIoViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.XmlIoPointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.XmlIoStitchingResults;
import util.URITools;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;

import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.jdom2.Element;

public class XmlIoSpimData2 extends XmlIoAbstractSpimData< SequenceDescription, SpimData2 >
{
	final XmlIoViewInterestPoints xmlViewsInterestPoints;
	final XmlIoBoundingBoxes xmlBoundingBoxes;
	final XmlIoPointSpreadFunctions xmlPointSpreadFunctions;
	final XmlIoStitchingResults xmlStitchingResults;
	final XmlIoIntensityAdjustments xmlIntensityAdjustments;

	URI lastURI;
	public static int numBackups = 5;
	public static boolean initN5Writing = true;

	public XmlIoSpimData2()
	{
		super( SpimData2.class, new XmlIoSequenceDescription(), new XmlIoViewRegistrations() );

		this.xmlViewsInterestPoints = new XmlIoViewInterestPoints();
		this.handledTags.add( xmlViewsInterestPoints.getTag() );

		this.xmlBoundingBoxes = new XmlIoBoundingBoxes();
		this.handledTags.add( xmlBoundingBoxes.getTag() );

		this.xmlPointSpreadFunctions = new XmlIoPointSpreadFunctions();
		this.handledTags.add( xmlPointSpreadFunctions.getTag() );

		this.xmlStitchingResults = new XmlIoStitchingResults();
		this.handledTags.add( xmlStitchingResults.getTag() );

		this.xmlIntensityAdjustments = new XmlIoIntensityAdjustments();
		this.handledTags.add( xmlIntensityAdjustments.getTag() );

		if ( initN5Writing )
		{
			try
			{
				// trigger the N5-blosc error, because if it is triggered for the first
				// time inside Spark, everything crashes
				new N5FSWriter(null);
			}
			catch (Exception e ) {}
		}
	}

	public SpimData2 load( final String xmlFilename ) throws SpimDataException
	{
		throw new RuntimeException( "This method is outdated and does not work anymore, use load( URI xmlURI )." );
	}

	@Override
	public void save( final SpimData2 spimData, String xmlURI ) throws SpimDataException
	{
		throw new RuntimeException( "This method is outdated and does not work anymore, use save( final SpimData2 spimData, URI xmlURI )." );
	}

	public SpimData2 load( final URI xmlURI ) throws SpimDataException
	{
		IOFunctions.println( "Loading: " + xmlURI.toString() );

		if ( URITools.isFile( xmlURI ) )
		{
			return super.load( xmlURI.toString() );
		}
		else if ( URITools.isS3( xmlURI ) )
		{
			throw new RuntimeException( "Not implemented yet." );
		}
		else if ( URITools.isGC( xmlURI ) )
		{
			throw new RuntimeException( "Not implemented yet." );
		}
		else
		{
			throw new RuntimeException( "Unsupported URI: " + xmlURI );
		}
	}

	//@Override
	public void save( final SpimData2 spimData, URI xmlURI ) throws SpimDataException
	{
		this.lastURI = xmlURI;

		// TODO: copy on cloud is different!
		// TODO: saving on the cloud is different
		throw new RuntimeException( "Not implemented yet." );

		/*
		// fist make a copy of the XML and save it to not loose it
		if ( new File( xmlFilename ).exists() )
		{
			int maxExistingBackup = 0;
			for ( int i = 1; i < numBackups; ++i )
				if ( new File( xmlFilename + "~" + i ).exists() )
					maxExistingBackup = i;
				else
					break;

			// copy the backups
			try
			{
				for ( int i = maxExistingBackup; i >= 1; --i )
					copyFile( new File( xmlFilename + "~" + i ), new File( xmlFilename + "~" + (i + 1) ) );

				copyFile( new File( xmlFilename ), new File( xmlFilename + "~1" ) );
			}
			catch ( final IOException e )
			{
				IOFunctions.println( "Could not save backup of XML file: " + e );
				e.printStackTrace();
			}
		}

		super.save( spimData, xmlFilename );
		*/
		// save also as zarr metadata object
	}

	public URI lastURI() { return lastURI; }

	protected static void copyFile( final File inputFile, final File outputFile ) throws IOException
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

	@Override
	public SpimData2 fromXml( final Element root, final URI xmlFile ) throws SpimDataException
	{
		final SpimData2 spimData = super.fromXml( root, xmlFile );
		final SequenceDescription seq = spimData.getSequenceDescription();

		final ViewInterestPoints viewsInterestPoints;
		Element elem = root.getChild( xmlViewsInterestPoints.getTag() );
		if ( elem == null )
		{
			viewsInterestPoints = new ViewInterestPoints();
			//viewsInterestPoints.createViewInterestPoints( seq.getViewDescriptions() );
		}
		else
		{
			viewsInterestPoints = xmlViewsInterestPoints.fromXml( elem, spimData.getBasePathURI(), seq.getViewDescriptions() );
		}
		spimData.setViewsInterestPoints( viewsInterestPoints );

		final BoundingBoxes boundingBoxes;
		elem = root.getChild( xmlBoundingBoxes.getTag() );
		if ( elem == null )
			boundingBoxes = new BoundingBoxes();
		else
			boundingBoxes = xmlBoundingBoxes.fromXml( elem );
		spimData.setBoundingBoxes( boundingBoxes );

		final PointSpreadFunctions psfs;
		elem = root.getChild( xmlPointSpreadFunctions.getTag() );
		if ( elem == null )
			psfs = new PointSpreadFunctions();
		else
			psfs = xmlPointSpreadFunctions.fromXml( elem, spimData.getBasePath() );
		spimData.setPointSpreadFunctions( psfs );

		final StitchingResults stitchingResults;
		elem = root.getChild( xmlStitchingResults.getTag() );
		if ( elem == null )
			stitchingResults = new StitchingResults();
		else
			stitchingResults = xmlStitchingResults.fromXml( elem );
		spimData.setStitchingResults( stitchingResults );

		final IntensityAdjustments intensityAdjustments;
		elem = root.getChild( xmlIntensityAdjustments.getTag() );
		if ( elem == null )
			intensityAdjustments = new IntensityAdjustments();
		else
			intensityAdjustments = xmlIntensityAdjustments.fromXml( elem );
		spimData.setIntensityAdjustments( intensityAdjustments );

		return spimData;
	}

	@Override
	public Element toXml( final SpimData2 spimData, final File xmlFileDirectory ) throws SpimDataException
	{
		final Element root = super.toXml( spimData, xmlFileDirectory );

		root.addContent( xmlViewsInterestPoints.toXml( spimData.getViewInterestPoints() ) );
		root.addContent( xmlBoundingBoxes.toXml( spimData.getBoundingBoxes() ) );
		root.addContent( xmlPointSpreadFunctions.toXml( spimData.getPointSpreadFunctions() ) );
		root.addContent( xmlStitchingResults.toXml( spimData.getStitchingResults() ) );
		root.addContent( xmlIntensityAdjustments.toXml( spimData.getIntensityAdjustments() ) );

		return root;
	}
}
