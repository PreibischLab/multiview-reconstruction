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
package net.preibisch.mvrecon.headless.interestpointdetection;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.legacy.io.TextFileAccess;

public class VisualizeSmFISH implements PlugIn
{
	public static int defaultImg = 0;
	public static String defaultCSV = "";
	public static int defaultRange = 2;

	public static ArrayList< double[] > readCSV( final String fn ) throws IOException
	{
		final BufferedReader in = TextFileAccess.openFileRead( fn );

		final String header = in.readLine().trim();

		// z,channel,x,y,prob,intens,intens_sig,x_offset_sigma,y_offset_sigma,z_offset_sigma
		System.out.println( "Header: " + header );

		final ArrayList< double[] > entries = new ArrayList<>();

		double minI = Double.MAX_VALUE;
		double maxI = -Double.MAX_VALUE;

		double minP = Double.MAX_VALUE;
		double maxP = -Double.MAX_VALUE;

		while ( in.ready() )
		{
			final String[] line = in.readLine().trim().split( "," );

			final double x = Double.parseDouble( line[ 3 ] );
			final double y = Double.parseDouble( line[ 2 ] );
			final double z = Double.parseDouble( line[ 0 ] ) - 1;

			final double p = Double.parseDouble( line[ 4 ] );
			final double i = Double.parseDouble( line[ 5 ] );

			minI = Math.min( i, minI );
			maxI = Math.max( i, maxI );

			minP = Math.min( p, minP );
			maxP = Math.max( p, maxP );

			entries.add( new double[] { x, y, z, p, i } );
		}

		IJ.log( "total number of spots read (no threshold): " + entries.size() );
		IJ.log( "minIntensity: " + minI );
		IJ.log( "maxIntensity: " + maxI );
		IJ.log( "minProbability: " + minP );
		IJ.log( "maxProbability: " + maxP );

		in.close();

		if ( maxP > 1.0 )
		{
			final double maxP1 = maxP;
			minP = Double.MAX_VALUE;
			maxP = -Double.MAX_VALUE;

			IJ.log( "Normalizing p to max(p)=1 " );

			for ( final double[] entry : entries )
			{
				entry[ 3 ] = ( entry[ 3 ] - 0 ) / ( maxP1 - 0 );

				minP = Math.min( entry[ 3 ], minP );
				maxP = Math.max( entry[ 3 ], maxP );
			}
		}

		int count = 0;
		for ( final double[] entry : entries )
			if ( entry[ 3 ] >= 0.1 )
				++count;

		IJ.log( "minProbability (norm): " + minP );
		IJ.log( "maxProbability (norm): " + maxP );

		IJ.log( "total number of spots read (threshold = 0.1): " + count );

		return entries;
	}

	@Override
	public void run( String args )
	{
		// get list of open image stacks
		final int[] idList = WindowManager.getIDList();

		if ( idList == null || idList.length == 0 )
		{
			IJ.error( "You need at least one open 3d image." );
			return;
		}

		// map all id's to image title for those who are 3d stacks
		final String[] imgList =
				Arrays.stream( idList ).
					//filter( id -> WindowManager.getImage( id ).getStackSize() > 1  ). // Cannot check here as id's are mixed up then
						mapToObj( id -> WindowManager.getImage( id ).getTitle() ).
							toArray( String[]::new );

		if ( defaultImg >= imgList.length )
			defaultImg = 0;

		final GenericDialogPlus gd = new GenericDialogPlus( "Visualize smFISH detection" );

		gd.addChoice( "Image_stack for visualization", imgList, imgList[ defaultImg ] );
		gd.addFileField( "CSV file", defaultCSV, 120 );
		gd.addNumericField( "Spot visible in +- z planes", defaultRange, 0 );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return;

		// don't do it by name as often multiple images have the same name
		try
		{
			final ImagePlus imp = WindowManager.getImage( idList[ defaultImg = gd.getNextChoiceIndex() ] );

			if ( imp == null )
			{
				IJ.log( "Cannot open image " );
				return;
			}

			final ArrayList< double[] > entries = readCSV( defaultCSV = gd.getNextString() );

			System.out.println( "Read: " + entries.size() + " points." );

			final int range = (int)Math.round( gd.getNextNumber() );

			imp.setRoi( new Rectangle( 0, 0, imp.getWidth()-1, imp.getHeight()-1 ) );
			imp.setDimensions( 1, imp.getStackSize(), 1 );
			imp.setSlice( imp.getStackSize() / 2 );
			imp.show();

			final InteractiveSmFISH idog = new InteractiveSmFISH( imp, entries, range );

			idog.run( null );

			while ( !idog.isFinished() )
			{
				try
				{
					Thread.sleep( 100 );
				}
				catch (InterruptedException e) {}
			}

			imp.close();

			if ( idog.wasCanceled() )
				return;

		}
		catch (IOException e)
		{
			IJ.log("Could not open CSV: " + defaultCSV + ": " + e );
			e.printStackTrace();
		}
	}

	public static void main( String[] args ) throws SpimDataException, IOException
	{
		new ImageJ();

		//ArrayList< double[] > entries = readCSV("/Users/spreibi/Documents/BIMSB/Projects/Dosage Compensation/raw_output.csv");
		//final ImagePlus imp = new ImagePlus("/Users/spreibi/Documents/BIMSB/Projects/Dosage Compensation/N2_352_ch0.tif");

		ArrayList< double[] > entries = readCSV("/Users/spreibi/Documents/Janelia/Projects/Srini smFISH/correct_nms.csv");
		final ImagePlus imp = new ImagePlus("/Users/spreibi/Documents/Janelia/Projects/Srini smFISH/sample.ALM_ch1.tif");

		System.out.println( "Read: " + entries.size() + " points." );

		if ( imp == null )
		{
			IJ.log( "Cannot open image " );
			return;
		}

		imp.setRoi( new Rectangle( 0, 0, imp.getWidth()-1, imp.getHeight()-1 ) );
		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.setSlice( imp.getStackSize() / 2 );
		imp.show();

		final InteractiveSmFISH idog = new InteractiveSmFISH( imp, entries, 2 );

		idog.run( null );

		while ( !idog.isFinished() )
		{
			try
			{
				Thread.sleep( 100 );
			}
			catch (InterruptedException e) {}
		}

		imp.close();

		if ( idog.wasCanceled() )
			return;

	}
}
