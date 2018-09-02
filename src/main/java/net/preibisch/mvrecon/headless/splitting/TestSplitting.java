package net.preibisch.mvrecon.headless.splitting;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import net.preibisch.mvrecon.process.splitting.SplittingTools;

public class TestSplitting
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;

		// generate 4 views with 1000 corresponding beads, single timepoint
		//spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );

		SpimData2 newSD = SplittingTools.splitImages( spimData, new long[] { 30, 30, 10 }, new long[] { 250, 250, 50 } );

		final ViewSetupExplorer< SpimData2, XmlIoSpimData2 > explorer = new ViewSetupExplorer<SpimData2, XmlIoSpimData2 >( newSD, "", null );
		explorer.getFrame().toFront();

		//new Data_Explorer()
	}

}
