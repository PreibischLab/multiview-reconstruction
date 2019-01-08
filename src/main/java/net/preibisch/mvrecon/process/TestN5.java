package net.preibisch.mvrecon.process;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5FSWriter;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class TestN5
{
	public static void main( String[] args ) throws SpimDataException, IOException
	{
		new ImageJ();

		SpimData2 spimData;

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		//spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Desktop/i2k/sim2/dataset.xml" );
		//spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Downloads/x-wing/dataset.xml" );
		
		N5FSWriter n5 = new N5FSWriter(spimData.getBasePath().getAbsolutePath() );
		n5.createGroup( test );
		
	}
}
