package net.preibisch.mvrecon.fiji.spimdata;

//import static mpicbg.spim.data.newstuff.SpimDataXmlKeys.*;

import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

public class SpimDataExample
{
	public static void main( final String[] args ) throws Exception
	{
		// load SpimData from xml file
		final String xmlFilename = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset_missing.xml";

		//final XmlIoSpimData< TimePoint, ViewSetupBeads > io = XmlIoSpimData.createDefault();
		//final XmlIoSpimData< TimePoint, ViewSetupBeads > io = XmlIo.createDefaultIo();

		//final SpimData< TimePoint, ViewSetupBeads > spimData = io.load( xmlFilename );

		final XmlIoSpimData2 io = new XmlIoSpimData2( "" );

		final SpimData2 spimData = io.load( xmlFilename );

		MissingViews m = spimData.getSequenceDescription().getMissingViews();

		if ( m != null )
			for ( final ViewId view : m.getMissingViews() )
				System.out.println( "Missing: " + view.getTimePointId() + " " + view.getViewSetupId() );

		for ( final ViewDescription vd : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( "Present: " + vd.isPresent() + " -- " + vd.getTimePointId() + " " + vd.getViewSetupId() );

		/*
		// save SpimData to xml file
		io.save( spimData, "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/example_fromdialog2.xml" );
		
		// write SpimData into a xml Document
		final Document doc = new Document( io.toXml( spimData, new File(".") ) );

		// output Document to System.out
		new XMLOutputter( Format.getPrettyFormat() ).output( doc, System.out );
		*/
	}
}
