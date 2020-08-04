package net.preibisch.mvrecon.headless.interestpointdetection;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;

import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.segmentation.InteractiveDoG;

public class InteractiveSmFISH extends InteractiveDoG
{
	// display +- range
	final int range = 2;

	final ArrayList< double[] > entries;
	final HashMap< Integer, ArrayList< Pair< double[], Integer > > > visibleEntries;

	public InteractiveSmFISH( final ImagePlus imp, final ArrayList< double[] > entries )
	{
		super( imp );

		this.thresholdMin = 0.000001f;

		this.entries = entries;
		this.visibleEntries = new HashMap<>();

		for ( int i = 0; i < imp.getNSlices(); ++i )
			visibleEntries.put( i, new ArrayList<>() );

		for ( final double[] entry : entries )
		{
			final int z = (int)Math.round( entry[ 2 ] );

			for ( int i = z - range; i <= z + range; ++i )
				if ( i >= 0 && i < imp.getNSlices() )
					visibleEntries.get( i ).add( new ValuePair<>( entry, Math.abs( z - i )) );
		}

		for ( int i = 0; i < imp.getNSlices(); ++i )
			System.out.println( "Slice " + i + ": " + visibleEntries.get( i ).size() );

	}

	@Override
	public void run( String arg )
	{
		super.run( arg );

		imp.setRoi( null, true );
		imp.updateAndDraw();
	}

	@Override
	protected void updatePreview( final ValueChange change )
	{
		// extract peaks to show
		Overlay o = imp.getOverlay();

		if ( o == null )
			imp.setOverlay( o = new Overlay() );

		o.clear();

		final int z = imp.getCurrentSlice()-1;

		for ( final Pair< double[], Integer > entry : visibleEntries.get( z ) )
		{
			final double x = entry.getA()[ 0 ];
			final double y = entry.getA()[ 1 ];
			final double p = entry.getA()[ 3 ];
			final float i = (float)entry.getA()[ 4 ];

			final int dist = entry.getB();
			final double sigma = this.sigma / (dist + 1);

			if ( p > threshold )
			{
				final OvalRoi or = new OvalRoi( Util.round( x - sigma ), Util.round( y - sigma ), Util.round( sigma*2 ), Util.round( sigma*2 ) );
				or.setStrokeColor( Color.green );
				
				or.setStrokeColor( new Color( 1.0f - i, i, 0, 1 ));
				
				o.add( or );
			}
		}

		imp.updateAndDraw();

		isComputing = false;

	}
}
