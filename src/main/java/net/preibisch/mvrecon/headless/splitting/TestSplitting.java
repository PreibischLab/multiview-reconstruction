/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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

		/*
			<size>2048 2048 900</size>
			<size>0.40625 0.40625 0.8125</size>
		 */

		//final String file = "/Volumes/home/Data/Expansion Microscopy/dataset.xml";
		final String file = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml";

		final String fileOut = file.replace( ".xml", ".split.xml" );

		System.out.println( "in: " + file );
		System.out.println( "out: " + fileOut );

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( file );

		//SpimData2 newSD = SplittingTools.splitImages( spimData, new long[] { 30, 30, 15 }, new long[] { 600, 600, 300 } );
		SpimData2 newSD = SplittingTools.splitImages( spimData, new long[] { 30, 30, 10 }, new long[] { 200, 200, 40 } );
		// drosophila with 1000 views

		final ViewSetupExplorer< SpimData2, XmlIoSpimData2 > explorer = new ViewSetupExplorer<SpimData2, XmlIoSpimData2 >( newSD, fileOut, new XmlIoSpimData2( "" ) );
		explorer.getFrame().toFront();
	}

}
