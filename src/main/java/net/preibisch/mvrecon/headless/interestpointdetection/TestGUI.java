package net.preibisch.mvrecon.headless.interestpointdetection;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import ij.ImageJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.legacy.io.TextFileAccess;

public class TestGUI
{
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

		System.out.println( minI + ", " + maxI );
		System.out.println( minP + ", " + maxP );
		in.close();

		if ( maxP > 1.0 )
		{
			System.out.println( "Normalizing p to 0 ... 1 " );
			for ( final double[] entry : entries )
				entry[ 3 ] = ( entry[ 3 ] - 0 ) / ( maxP - 0 );
		}

		return entries;
	}

	public static void main( String[] args ) throws SpimDataException, IOException
	{
		new ImageJ();

		ArrayList< double[] > entries = readCSV("/Users/spreibi/Documents/BIMSB/Projects/Dosage Compensation/raw_output.csv");
		final ImagePlus imp = new ImagePlus("/Users/spreibi/Documents/BIMSB/Projects/Dosage Compensation/N2_352_ch0.tif");

		//ArrayList< double[] > entries = readCSV("/Users/spreibi/Documents/Janelia/Projects/Srini smFISH/results_superv_a.csv");
		//final ImagePlus imp = new ImagePlus("/Users/spreibi/Documents/Janelia/Projects/Srini smFISH/sample.ALM_ch1.tif");

		System.out.println( "Read: " + entries.size() + " points." );

		if ( imp == null )
			return;

		imp.setRoi( new Rectangle( 0, 0, imp.getWidth()-1, imp.getHeight()-1 ) );
		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.setSlice( imp.getStackSize() / 2 );
		imp.show();

		final InteractiveSmFISH idog = new InteractiveSmFISH( imp, entries );

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
