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
package net.preibisch.mvrecon.fiji.plugin;

import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.Img;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;

public class Visualize_Detections implements PlugIn
{
	public static String[] detectionsChoice = new String[]{ "All detections", "Corresponding detections" };
	public static int defaultDetections = 0;
	public static double defaultDownsample = 1.0;
	public static boolean defaultDisplayInput = false;

	public static class Params
	{
		final public String label;
		final public int detections;
		final public double downsample;
		final public boolean displayInput;

		public Params( final String label, final int detections, final double downsample, final boolean displayInput )
		{
			this.label = label;
			this.detections = detections;
			this.downsample = downsample;
			this.displayInput = displayInput;
		}
	}

	@Override
	public void run( final String arg0 )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "visualize detections", true, true, true, true, true ) )
			return;

		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );
		final Params params = queryDetails( result.getData(), viewIds );

		if ( params != null )
			visualize( result.getData(), viewIds, params.label,params.detections, params.downsample, params.displayInput );
	}

	public static Params queryDetails( final SpimData2 spimData, final List< ViewId > viewIds )
	{
		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose segmentations to display" );

		final String[] labels = InterestPointTools.getAllInterestPointLabels( spimData, viewIds );

		if ( labels.length == 0 )
		{
			IOFunctions.printErr( "No interest points available, stopping. Please run Interest Point Detection first" );
			return null;
		}

		// choose the first label that is complete if possible
		if ( Interest_Point_Registration.defaultLabel < 0 || Interest_Point_Registration.defaultLabel >= labels.length )
		{
			Interest_Point_Registration.defaultLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					Interest_Point_Registration.defaultLabel = i;
					break;
				}

			if ( Interest_Point_Registration.defaultLabel == -1 )
				Interest_Point_Registration.defaultLabel = 0;
		}

		gd.addChoice( "Interest_points" , labels, labels[ Interest_Point_Registration.defaultLabel ] );

		gd.addChoice( "Display", detectionsChoice, detectionsChoice[ defaultDetections ] );
		gd.addNumericField( "Downsample_detections_rendering", defaultDownsample, 2, 4, "times" );
		gd.addCheckbox( "Display_input_images", defaultDisplayInput );
		
		GUIHelper.addWebsite( gd );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		// assemble which label has been selected
		final String label = InterestPointTools.getSelectedLabel( labels, Interest_Point_Registration.defaultLabel = gd.getNextChoiceIndex() );

		IOFunctions.println( "displaying label: '" + label + "'" );
		
		final int detections = defaultDetections = gd.getNextChoiceIndex();
		final double downsample = defaultDownsample = gd.getNextNumber();
		final boolean displayInput = defaultDisplayInput = gd.getNextBoolean();

		return new Params( label, detections, downsample, displayInput );
	}

	public static void visualize(
			final SpimData2 spimData,
			final List< ViewId > viewIds,
			final String label,
			final int detections,
			final double downsample,
			final boolean displayInput )
	{
		//
		// load the images and render the segmentations
		//
		final DisplayImage di = new DisplayImage();

		for ( final ViewId viewId : viewIds )
		{
			// get the viewdescription
			final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId.getTimePointId(), viewId.getViewSetupId() );

			// check if the view is present
			if ( !vd.isPresent() )
				continue;

			// load and display
			final String name = "TPId" + vd.getTimePointId() + "_SetupId" + vd.getViewSetupId() + "+(label='" + label + "')";
			final Interval interval;
			
			if ( displayInput )
			{
				@SuppressWarnings( "unchecked" )
				final RandomAccessibleInterval< UnsignedShortType > img = ( RandomAccessibleInterval< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId() );
				di.exportImage( img, name );
				interval = img;
			}
			else
			{
				if ( !vd.getViewSetup().hasSize() )
				{
					IOFunctions.println( "Cannot load image dimensions from XML for " + name + ", using min/max of all detections instead." );
					interval = null;
				}
				else
				{
					interval = new FinalInterval( vd.getViewSetup().getSize() );
				}
			}
			
			di.exportImage( renderSegmentations( spimData, viewId, label, detections, interval, downsample ), "seg of " + name );
		}
	}
	
	protected static Img< UnsignedShortType > renderSegmentations(
			final SpimData2 data,
			final ViewId viewId,
			final String label,
			final int detections,
			Interval interval,
			final double downsample )
	{
		final InterestPointList ipl = data.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
		final List< InterestPoint > list = ipl.getInterestPointsCopy();

		if ( interval == null )
		{
			final int n = list.get( 0 ).getL().length;

			final long[] min = new long[ n ];
			final long[] max = new long[ n ];

			for ( int d = 0; d < n; ++d )
			{
				min[ d ] = Math.round( list.get( 0 ).getL()[ d ] ) - 1;
				max[ d ] = Math.round( list.get( 0 ).getL()[ d ] ) + 1;
			}

			for ( final InterestPoint ip : list )
			{
				for ( int d = 0; d < n; ++d )
				{
					min[ d ] = Math.min( min[ d ], Math.round( ip.getL()[ d ] ) - 1 );
					max[ d ] = Math.max( max[ d ], Math.round( ip.getL()[ d ] ) + 1 );
				}
			}
			
			interval = new FinalInterval( min, max );
		}
		
		// downsample
		final long[] min = new long[ interval.numDimensions() ];
		final long[] max = new long[ interval.numDimensions() ];
		
		for ( int d = 0; d < interval.numDimensions(); ++d )
		{
			min[ d ] = Math.round( interval.min( d ) / downsample );
			max[ d ] = Math.round( interval.max( d ) / downsample ) ;
		}
		
		interval = new FinalInterval( min, max );
	
		final Img< UnsignedShortType > s = new ImagePlusImgFactory< UnsignedShortType >().create( interval, new UnsignedShortType() );
		final RandomAccess< UnsignedShortType > r = Views.extendZero( s ).randomAccess();
		
		final int n = s.numDimensions();
		final long[] tmp = new long[ n ];
		
		if ( detections == 0 )
		{
			IOFunctions.println( "Visualizing " + list.size() + " detections." );
			
			for ( final InterestPoint ip : list )
			{
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( ip.getL()[ d ] / downsample );
	
				r.setPosition( tmp );
				r.get().set( 65535 );
			}
		}
		else
		{
			final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();

			for ( final InterestPoint ip : list )
				map.put( ip.getId(), ip );

			final List< CorrespondingInterestPoints > cList = ipl.getCorrespondingInterestPointsCopy();

			if ( cList.size() == 0 )
			{
				IOFunctions.println( "No corresponding detections available, the dataset was not registered using these detections." );
				return s;
			}

			IOFunctions.println( "Visualizing " + cList.size() + " corresponding detections." );

			for ( final CorrespondingInterestPoints ip : cList )
			{
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( map.get( ip.getDetectionId() ).getL()[ d ] / downsample );
	
				r.setPosition( tmp );
				r.get().set( 65535 );
			}
		}

		try
		{
			Gauss3.gauss( new double[]{ 2, 2, 2 }, Views.extendZero( s ), s );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Gaussian Convolution of detections failed: " + e );
			e.printStackTrace();
		}
		catch ( OutOfMemoryError e )
		{
			IOFunctions.println( "Gaussian Convolution of detections failed due to out of memory, just showing plain image: " + e );
		}
		
		return s;
	}
	
	public static void main( final String[] args )
	{
		new ImageJ();
		new Visualize_Detections().run( null );
	}

}
