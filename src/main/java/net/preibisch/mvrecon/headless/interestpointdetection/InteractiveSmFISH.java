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
	final ArrayList< double[] > entries;
	final HashMap< Integer, ArrayList< Pair< double[], Integer > > > visibleEntries;

	public InteractiveSmFISH( final ImagePlus imp, final ArrayList< double[] > entries, final int range )
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
			final float p = (float)entry.getA()[ 3 ];
			final float i = (float)entry.getA()[ 4 ];

			final int dist = entry.getB();
			final double sigma = this.sigma / (dist + 1);

			if ( p > threshold )
			{
				final OvalRoi or = new OvalRoi( Util.round( x - sigma ), Util.round( y - sigma ), Util.round( sigma*2 ), Util.round( sigma*2 ) );
				or.setStrokeColor( Color.green );
				
				or.setStrokeColor( new Color( 1.0f - p, i, 0, 1 ));
				
				o.add( or );
			}
		}

		imp.updateAndDraw();

		isComputing = false;

	}
}
