package net.preibisch.mvrecon.fiji.spimdata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.XmlIoBoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.XmlIoViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.XmlIoPointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.XmlIoStitchingResults;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;
import mpicbg.spim.io.IOFunctions;

import org.jdom2.Element;

public class XmlIoSpimData2 extends XmlIoAbstractSpimData< SequenceDescription, SpimData2 >
{
	final XmlIoViewInterestPoints xmlViewsInterestPoints;
	final XmlIoBoundingBoxes xmlBoundingBoxes;
	final XmlIoPointSpreadFunctions xmlPointSpreadFunctions;
	final XmlIoStitchingResults xmlStitchingResults;

	String clusterExt, lastFileName;
	public static int numBackups = 5;
	
	public XmlIoSpimData2( final String clusterExt )
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

		this.clusterExt = clusterExt;
	}

	public void setClusterExt( final String clusterExt ) { this.clusterExt = clusterExt; }

	@Override
	public void save( final SpimData2 spimData, String xmlFilename ) throws SpimDataException
	{
		if ( clusterExt != null && clusterExt.length() > 0 )
		{
			if ( xmlFilename.toLowerCase().endsWith( ".xml" ) )
			{
				xmlFilename =
						xmlFilename.substring( 0, xmlFilename.length() - 4 ) + "." + this.clusterExt +
						xmlFilename.substring( xmlFilename.length() - 4, xmlFilename.length() );
			}
			else
			{
				xmlFilename += this.clusterExt + ".xml";
			}
		}

		this.lastFileName = xmlFilename;

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
	}

	public String lastFileName() { return lastFileName; }

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
	public SpimData2 fromXml( final Element root, final File xmlFile ) throws SpimDataException
	{
		final SpimData2 spimData = super.fromXml( root, xmlFile );
		final SequenceDescription seq = spimData.getSequenceDescription();

		final ViewInterestPoints viewsInterestPoints;
		Element elem = root.getChild( xmlViewsInterestPoints.getTag() );
		if ( elem == null )
		{
			viewsInterestPoints = new ViewInterestPoints();
			viewsInterestPoints.createViewInterestPoints( seq.getViewDescriptions() );
		}
		else
		{
			viewsInterestPoints = xmlViewsInterestPoints.fromXml( elem, spimData.getBasePath(), seq.getViewDescriptions() );
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
		spimData.setStitchingResults(  stitchingResults );

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

		return root;
	}
}
