/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointremoval.DistanceHistogram;
import net.preibisch.mvrecon.process.interestpointremoval.RelativeDistanceHistogram;

public class Show_Relative_Histogram implements PlugIn
{
	@Override
	public void run( final String arg )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "", true, true, true, true, true ) )
			return;

		final SpimData2 data = xml.getData();

		plotHistogram( data, SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() ) );
	}

	public static boolean plotHistogram( final SpimData2 spimData, final Collection< ? extends ViewId > viewCollection )
	{
		// filter not present ViewIds
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		final List< ViewId > removed  = SpimData2.filterMissingViews( spimData, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final String[] labels = getLabels( spimData, viewIds );

		if ( labels == null )
			return false;

		RelativeDistanceHistogram.plotHistogram( spimData, viewIds, labels[ 0 ], labels[ 1 ], DistanceHistogram.getHistogramTitle( viewIds ) );

		return false;
	}

	public static String[] getLabels(
			final SpimData2 spimData,
			final List< ViewId > viewIds )
	{
		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose interest point labels" );

		final String[] labels = InterestPointTools.getAllInterestPointLabels( spimData, viewIds );

		// choose the first label that is complete if possible
		if ( ThinOut_Detections.defaultLabel < 0 || ThinOut_Detections.defaultLabel >= labels.length )
		{
			ThinOut_Detections.defaultLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					ThinOut_Detections.defaultLabel = i;
					break;
				}

			if ( ThinOut_Detections.defaultLabel == -1 )
				ThinOut_Detections.defaultLabel = 0;
		}

		// choose the first label that is complete if possible
		if ( RelativeThinOut_Detections.defaultRelativeLabel < 0 || RelativeThinOut_Detections.defaultRelativeLabel >= labels.length )
		{
			RelativeThinOut_Detections.defaultRelativeLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					RelativeThinOut_Detections.defaultRelativeLabel = i;
					break;
				}

			if ( RelativeThinOut_Detections.defaultRelativeLabel == -1 )
				RelativeThinOut_Detections.defaultRelativeLabel = 0;
		}

		gd.addChoice( "Interest_points", labels, labels[ ThinOut_Detections.defaultLabel ] );
		gd.addChoice( "Relative_to", labels, labels[ RelativeThinOut_Detections.defaultRelativeLabel ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		// assemble which label has been selected
		final String[] labelSelection = new String[ 2 ];

		labelSelection[ 0 ] = InterestPointTools.getSelectedLabel( labels, ThinOut_Detections.defaultLabel = gd.getNextChoiceIndex() );
		labelSelection[ 1 ] = InterestPointTools.getSelectedLabel( labels, RelativeThinOut_Detections.defaultRelativeLabel = gd.getNextChoiceIndex() );

		return labelSelection;
	}
}
