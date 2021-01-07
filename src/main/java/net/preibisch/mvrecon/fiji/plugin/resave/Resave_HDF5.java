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
package net.preibisch.mvrecon.fiji.plugin.resave;

import ij.plugin.PlugIn;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Dimensions;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.ValuePair;
import net.preibisch.mvrecon.fiji.plugin.Toggle_Cluster_Options;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;

public class Resave_HDF5 implements PlugIn
{
	public static void main( final String[] args )
	{
		new Resave_HDF5().run( null );
	}

	@Override
	public void run( final String arg0 )
	{
		boolean rememberClusterProcessing = Toggle_Cluster_Options.displayClusterProcessing;
		Toggle_Cluster_Options.displayClusterProcessing = false;

		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "Resaving as HDF5", "Resave", true, true, true, true, true ) )
			return;

		Toggle_Cluster_Options.displayClusterProcessing = rememberClusterProcessing;

		// load all dimensions if they are not known (required for estimating the mipmap layout)
		if ( loadDimensions( xml.getData(), xml.getViewSetupsToProcess() ) )
		{
			// save the XML again with the dimensions loaded
			SpimData2.saveXML( xml.getData(), xml.getXMLFileName(), xml.getClusterExtension() );
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = proposeMipmaps( xml.getViewSetupsToProcess() );

		Generic_Resave_HDF5.lastExportPath = LoadParseQueryXML.defaultXMLfilename;

		final int firstviewSetupId = xml.getData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final Parameters params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, true );

		if ( params == null )
			return;

		LoadParseQueryXML.defaultXMLfilename = params.getSeqFile().toString();

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		final SpimData2 data = xml.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() );

		// write hdf5
		Generic_Resave_HDF5.writeHDF5( reduceSpimData2( data, viewIds ), params, progressWriter );

		// write xml sequence description
		if ( !params.onlyRunSingleJob || params.jobId == 0 )
		{
			try
			{
				final Pair< SpimData2, List< String > > result = createXMLObject( data, viewIds, params, progressWriter, false );

				xml.getIO().save( result.getA(), params.seqFile.getAbsolutePath() );
				progressWriter.setProgress( 0.95 );
				
				// copy the interest points if they exist
				Resave_TIFF.copyInterestPoints( xml.getData().getBasePath(), params.getSeqFile().getParentFile(), result.getB() );
			}
			catch ( SpimDataException e )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.getSeqFile() + "': " + e );
				throw new RuntimeException( e );
			}
			finally
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.getSeqFile() + "'." );
			}
		}
		progressWriter.setProgress( 1.0 );
		progressWriter.out().println( "done" );
	}

	public static Map< Integer, ExportMipmapInfo > proposeMipmaps( final Collection< ? extends BasicViewSetup > viewsetups )
	{
		final HashMap< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
		for ( final BasicViewSetup setup : viewsetups )
		{
			final ExportMipmapInfo mi = ProposeMipmaps.proposeMipmaps( setup );

			// Sometimes this is negative (not sure what's going on there)
			for ( int l = 0; l < mi.getNumLevels(); ++l )
				if ( mi.getSubdivisions()[l][2] < 0 )
					mi.getSubdivisions()[l][2] = 32;

			// 2d case
			if ( setup.hasSize() && setup.getSize().dimension( 2 ) == 1 )
				for ( int l = 0; l < mi.getNumLevels(); ++l )
					mi.getSubdivisions()[l][2] = 1;

			perSetupExportMipmapInfo.put( setup.getId(), mi );
		}

		return perSetupExportMipmapInfo;
	}


	public static boolean loadDimensions( final SpimData2 spimData, final List< ViewSetup > viewsetups )
	{
		boolean loadedDimensions = false;

		for ( final ViewSetup vs : viewsetups )
		{
			if ( vs.getSize() == null )
			{
				IOFunctions.println( "Dimensions of viewsetup " + vs.getId() + " unknown. Loading them ... " );

				for ( final TimePoint t : spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
				{
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( t.getId(), vs.getId() );

					if ( vd.isPresent() )
					{
						Dimensions dim = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImageSize( vd.getTimePointId() );

						IOFunctions.println(
								"Dimensions: " + dim.dimension( 0 ) + "x" + dim.dimension( 1 ) + "x" + dim.dimension( 2 ) +
								", loaded from tp:" + t.getId() + " vs: " + vs.getId() );

						vs.setSize( dim );
						loadedDimensions = true;
						break;
					}
					else
					{
						IOFunctions.println( "ViewSetup: " + vs.getId() + " not present in timepoint: " + t.getId() );
					}
				}
			}
		}

		return loadedDimensions;
	}

	/**
	 * Reduces a given SpimData2 to the subset of timepoints and viewsetups as selected by the user, including the original imgloader.
	 *
	 * @param oldSpimData - the original SpimData
	 * @param viewIds - the views to keep
	 * @return - reduced SpimData2
	 */
	public static SpimData2 reduceSpimData2( final SpimData2 oldSpimData, final List< ViewId > viewIds )
	{
		final TimePoints timepoints;

		try
		{
			timepoints = new TimePointsPattern( Resave_TIFF.listAllTimePoints( SpimData2.getAllTimePointsSorted( oldSpimData, viewIds ) ) );
		}
		catch (ParseException e)
		{
			IOFunctions.println( "Automatically created list of timepoints failed to parse. This should not happen, really :) -- " + e );
			IOFunctions.println( "Here is the list: " + Resave_TIFF.listAllTimePoints( SpimData2.getAllTimePointsSorted( oldSpimData, viewIds ) ) );
			e.printStackTrace();
			return null;
		}

		final List< ViewSetup > viewSetupsToProcess = SpimData2.getAllViewSetupsSorted( oldSpimData, viewIds );

		// a hashset for all viewsetups that remain
		final Set< ViewId > views = new HashSet< ViewId >();

		for ( final ViewId viewId : viewIds )
			views.add( new ViewId( viewId.getTimePointId(), viewId.getViewSetupId() ) );

		final MissingViews oldMissingViews = oldSpimData.getSequenceDescription().getMissingViews();
		final HashSet< ViewId > missingViews = new HashSet< ViewId >();

		if( oldMissingViews != null && oldMissingViews.getMissingViews() != null )
			for ( final ViewId id : oldMissingViews.getMissingViews() )
				if ( views.contains( id ) )
					missingViews.add( id );

		// add the new missing views!!!
		for ( final TimePoint t : timepoints.getTimePointsOrdered() )
			for ( final ViewSetup v : viewSetupsToProcess )
			{
				final ViewId viewId = new ViewId( t.getId(), v.getId() );

				if ( !views.contains( viewId ) )
					missingViews.add( viewId );
			}

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, viewSetupsToProcess, oldSpimData.getSequenceDescription().getImgLoader(), new MissingViews( missingViews ) );

		// re-assemble the registrations
		final Map< ViewId, ViewRegistration > oldRegMap = oldSpimData.getViewRegistrations().getViewRegistrations();
		final Map< ViewId, ViewRegistration > newRegMap = new HashMap< ViewId, ViewRegistration >();

		for ( final ViewId viewId : oldRegMap.keySet() )
			if ( views.contains( viewId ) )
				newRegMap.put( viewId, oldRegMap.get( viewId ) );

		final ViewRegistrations viewRegistrations = new ViewRegistrations( newRegMap );

		// re-assemble the interestpoints and a list of filenames to copy
		final Map< ViewId, ViewInterestPointLists > oldInterestPoints = oldSpimData.getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, ViewInterestPointLists > newInterestPoints = new HashMap< ViewId, ViewInterestPointLists >();

		for ( final ViewId viewId : oldInterestPoints.keySet() )
			if ( views.contains( viewId ) )
				newInterestPoints.put( viewId, oldInterestPoints.get( viewId ) );

		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( newInterestPoints );

		//TODO: copy PSFs

		final SpimData2 newSpimData = new SpimData2(
				oldSpimData.getBasePath(),
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints,
				oldSpimData.getBoundingBoxes(),
				oldSpimData.getPointSpreadFunctions(),
				oldSpimData.getStitchingResults(),
				oldSpimData.getIntensityAdjustments() );

		return newSpimData;
	}

	public static Pair< SpimData2, List< String > > createXMLObject(
			final SpimData2 spimData,
			final List< ViewId > viewIds,
			final Parameters params,
			final ProgressWriter progressWriter,
			final boolean useRightAway )
	{
		// Re-assemble a new SpimData object containing the subset of viewsetups and timepoints selected
		final List< String > filesToCopy = new ArrayList< String >();
		final SpimData2 newSpimData = Resave_TIFF.assemblePartialSpimData2( spimData, viewIds, params.seqFile.getParentFile(), filesToCopy );
		final ArrayList< Partition > partitions = Generic_Resave_HDF5.getPartitions( newSpimData, params );

		final Hdf5ImageLoader hdf5Loader;

		if ( useRightAway )
			hdf5Loader = new Hdf5ImageLoader( params.hdf5File, partitions, newSpimData.getSequenceDescription(), true );
		else
			hdf5Loader = new Hdf5ImageLoader( params.hdf5File, partitions, null, false );
		
		newSpimData.getSequenceDescription().setImgLoader( hdf5Loader );
		newSpimData.setBasePath( params.seqFile.getParentFile() );

		return new ValuePair< SpimData2, List< String > >( newSpimData, filesToCopy );
	}
}
