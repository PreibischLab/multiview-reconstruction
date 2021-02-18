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
package net.preibisch.mvrecon.process.export;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF.Parameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.FileMapImgLoaderLOCI;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ExportSpimData2TIFF implements ImgExport
{
	File path;
	List< TimePoint > newTimepoints;
	List< ViewSetup > newViewSetups;
	FusionExportInterface fusion;
	HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap = new HashMap<>();

	Parameters params;
	Save3dTIFF saver;
	SpimData2 newSpimData;

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval<T> img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup )
	{
		return exportImage( img, bb, downsampling, anisoF, title, fusionGroup, Double.NaN, Double.NaN );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval<T> img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final double min,
			final double max )
	{
		// write the image
		if ( !this.saver.exportImage( img, bb, downsampling, anisoF, title, fusionGroup, min, max ) )
			return false;

		final ViewId newViewId = identifyNewViewId( newTimepoints, newViewSetups, fusionGroup, fusion );
		final ViewDescription newVD = newSpimData.getSequenceDescription().getViewDescription( newViewId );

		// populate HashMap for the ImgLoader
		fileMap.put( newVD, new ValuePair< File, Pair<Integer,Integer> >( new File( saver.getFileName( title ) ), new ValuePair<>( 0, 0 ) ) );

		// update the registrations
		final ViewRegistration vr = newSpimData.getViewRegistrations().getViewRegistration( newViewId );

		final double scale = Double.isNaN( downsampling ) ? 1.0 : downsampling;
		final double ai = Double.isNaN( anisoF ) ? 1.0 : anisoF;

		final AffineTransform3D m = new AffineTransform3D();
		m.set( scale, 0.0f, 0.0f, bb.min( 0 ), 
			   0.0f, scale, 0.0f, bb.min( 1 ),
			   0.0f, 0.0f, scale * ai, bb.min( 2 ) * ai ); // TODO: bb * ai is right?
		final ViewTransform vt = new ViewTransformAffine( "fusion bounding box", m );

		vr.getTransformList().clear();
		vr.getTransformList().add( vt );
		
		return true;
	}

	@Override
	public boolean finish()
	{
		final FileMapImgLoaderLOCI imgLoader = new FileMapImgLoaderLOCI(
				fileMap, new CellImgFactory<>(), newSpimData.getSequenceDescription() );

		newSpimData.getSequenceDescription().setImgLoader( imgLoader );

		XmlIoSpimData2 io = new XmlIoSpimData2( "" );

		try
		{
			io.save( newSpimData, new File( params.getXMLFile() ).getAbsolutePath() );
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + io.lastFileName() + "'." );

			// this spimdata object was not modified, we just wrote a new one
			return false;
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + io.lastFileName() + "'." );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		if ( Resave_TIFF.defaultPath == null )
			Resave_TIFF.defaultPath = "";
		
		this.params = Resave_TIFF.getParameters();
		
		if ( this.params == null )
			return false;

		this.path = new File( new File( this.params.getXMLFile() ).getParent() );
		this.saver = new Save3dTIFF( this.path.toString(), this.params.compress() );

		// define new timepoints and viewsetups
		final Pair< List< TimePoint >, List< ViewSetup > > newStructure = defineNewViewSetups( fusion, fusion.getDownsampling(), fusion.getAnisotropyFactor() );
		this.newTimepoints = newStructure.getA();
		this.newViewSetups = newStructure.getB();

		this.fusion = fusion; // we need it later to find the right new ViewId for a FusionGroup
		this.newSpimData = createSpimData2( newTimepoints, newViewSetups, params );

		return true;
	}

	@Override
	public ImgExport newInstance() { return new ExportSpimData2TIFF(); }

	@Override
	public String getDescription() { return "Save as new XML Project (TIFF)"; }

	public static ViewId identifyNewViewId(
			final List< TimePoint > newTimepoints,
			final List< ViewSetup > newViewSetups,
			final Group< ? extends ViewId > fusionGroup,
			final FusionExportInterface fusion )
	{
		if ( fusion.getSplittingType() == 0 ) // "Each timepoint & channel"
		{
			final ViewDescription old = fusion.getSpimData().getSequenceDescription().getViewDescription( fusionGroup.iterator().next() );

			TimePoint tpNew = old.getTimePoint(); // stays the same
			ViewSetup vsNew = null;

			final int oc = old.getViewSetup().getChannel().getId();

			for ( final ViewSetup vs : newViewSetups )
				if ( vs.getChannel().getId() == oc )
					vsNew = vs;

			return new ViewId( tpNew.getId(), vsNew.getId() );
		}
		else if ( fusion.getSplittingType() == 1 ) // "Each timepoint, channel & illumination"
		{
			final ViewDescription old = fusion.getSpimData().getSequenceDescription().getViewDescription( fusionGroup.iterator().next() );

			TimePoint tpNew = old.getTimePoint(); // stays the same
			ViewSetup vsNew = null;

			final int oc = old.getViewSetup().getChannel().getId();
			final int oi = old.getViewSetup().getIllumination().getId();
	
			for ( final ViewSetup vs : newViewSetups )
				if ( vs.getChannel().getId() == oc && vs.getIllumination().getId() == oi )
					vsNew = vs;

			return new ViewId( tpNew.getId(), vsNew.getId() );
		}
		else if ( fusion.getSplittingType() == 2 ) // "All views together"
		{
			return new ViewId( 0, 0 );
		}
		else if ( fusion.getSplittingType() == 3 ) // "Each view" 
		{
			return fusion.getSpimData().getSequenceDescription().getViewDescription( fusionGroup.iterator().next() );
		}
		else
		{
			throw new RuntimeException( "SplittingType " + fusion.getSplittingType() + " unknown." );
		}
	}

	public static Pair< List< TimePoint >, List< ViewSetup > > defineNewViewSetups( final FusionExportInterface fusion, double downsampling, double anisoF )
	{
		final List< ViewSetup > newViewSetups = new ArrayList<>();
		final List< TimePoint > newTimepoints;

		int newViewSetupId = 0;

		downsampling = Double.isNaN( downsampling ) ? 1.0 : downsampling;
		anisoF = Double.isNaN( anisoF ) ? 1.0 : anisoF;

		if ( fusion.getSplittingType() < 2 ) // "Each timepoint & channel" or "Each timepoint, channel & illumination"
		{
			newTimepoints = SpimData2.getAllTimePointsSorted( fusion.getSpimData(), fusion.getViews() );

			final List< Channel > channels = SpimData2.getAllChannelsSorted( fusion.getSpimData(), fusion.getViews() );

			if ( fusion.getSplittingType() == 0 )// "Each timepoint & channel"
			{
				for ( final Channel c : channels )
					newViewSetups.add(
						new ViewSetup(
							newViewSetupId++,
							c.getName(),
							fusion.getDownsampledBoundingBox(),
							new FinalVoxelDimensions( "px", new double[] { downsampling, downsampling, downsampling * anisoF } ),
							new Tile( 0 ),
							c,
							new Angle( 0 ),
							new Illumination( 0 ) ) );
			}
			else // "Each timepoint, channel & illumination"
			{
				final List< Illumination > illums = SpimData2.getAllIlluminationsSorted( fusion.getSpimData(), fusion.getViews() );

				for ( int c = 0; c < channels.size(); ++c )
					for ( int i = 0; i < illums.size(); ++i )
							newViewSetups.add(
								new ViewSetup(
									newViewSetupId++,
									channels.get( c ).getName() + "_" + illums.get( i ).getName(),
									fusion.getDownsampledBoundingBox(),
									new FinalVoxelDimensions( "px", new double[] { downsampling, downsampling, downsampling * anisoF } ),
									new Tile( 0 ),
									channels.get( c ),
									new Angle( 0 ),
									illums.get( i ) ) );
			}
		}
		else if ( fusion.getSplittingType() == 2 ) // "All views together"
		{
			newTimepoints = new ArrayList<>();
			newTimepoints.add( new TimePoint( 0 ) );

			newViewSetups.add(
					new ViewSetup(
							0,
							"Fused",
							fusion.getDownsampledBoundingBox(),
							new FinalVoxelDimensions( "px", new double[] { downsampling, downsampling, downsampling * anisoF } ),
							new Tile( 0 ),
							new Channel( 0 ),
							new Angle( 0 ),
							new Illumination( 0 ) ) );
		}
		else if ( fusion.getSplittingType() == 3 ) // "Each view"
		{
			newTimepoints = new ArrayList<>();
			for ( final TimePoint tp : SpimData2.getAllTimePointsSorted( fusion.getSpimData(), fusion.getViews() ) )
				newTimepoints.add( tp );

			for ( final ViewSetup vs : SpimData2.getAllViewSetupsSorted( fusion.getSpimData(), fusion.getViews() ) )
			{
				newViewSetups.add(
						new ViewSetup(
								vs.getId(),
								vs.getName(),
								fusion.getDownsampledBoundingBox(),
								new FinalVoxelDimensions( "px", new double[] { downsampling, downsampling, downsampling * anisoF } ),
								vs.getTile(),
								vs.getChannel(),
								vs.getAngle(),
								vs.getIllumination() ) );
			}
		}
		else
		{
			throw new RuntimeException( "SplittingType " + fusion.getSplittingType() + " unknown." );
		}

		return new ValuePair< List< TimePoint >, List< ViewSetup > >( newTimepoints, newViewSetups );
	}

	protected SpimData2 createSpimData2(
			final List< TimePoint > timepointsToProcess,
			final List< ViewSetup > viewSetupsToProcess,
			final Parameters params )
	{
		// Assemble a new SpimData object containing the subset of viewsetups and timepoints
		return assembleSpimData2( timepointsToProcess, viewSetupsToProcess, new File( params.getXMLFile() ).getParentFile() );
	}

	/**
	 * Assembles a new SpimData2 based on the timepoints and viewsetups.
	 * The imgloader is still not set here.
	 * @param timepointsToProcess TimePoints to process
	 * @param viewSetupsToProcess ViewSetups to process
	 * @param basePath the base path
	 * @return the new SpimData2
	 */
	public static SpimData2 assembleSpimData2( 
			final List< TimePoint > timepointsToProcess,
			final List< ViewSetup > viewSetupsToProcess,
			final File basePath )
	{
		final TimePoints timepoints;

		try
		{
			timepoints = new TimePointsPattern( Resave_TIFF.listAllTimePoints( timepointsToProcess ) );
		}
		catch (ParseException e)
		{
			IOFunctions.println( "Automatically created list of timepoints failed to parse. This should not happen, really :) -- " + e );
			IOFunctions.println( "Here is the list: " + Resave_TIFF.listAllTimePoints( timepointsToProcess ) );
			e.printStackTrace();
			return null;
		}
		
		final MissingViews missingViews = new MissingViews( new ArrayList< ViewId >() );
				
		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, viewSetupsToProcess, null, missingViews );

		// assemble the viewregistrations
		final Map< ViewId, ViewRegistration > regMap = new HashMap< ViewId, ViewRegistration >();
		
		for ( final ViewDescription vDesc : sequenceDescription.getViewDescriptions().values() )
		{
			final ViewRegistration viewRegistration = new ViewRegistration( vDesc.getTimePointId(), vDesc.getViewSetupId() );
			viewRegistration.identity();
			regMap.put( viewRegistration, viewRegistration );
		}
		
		final ViewRegistrations viewRegistrations = new ViewRegistrations( regMap );
		
		// assemble the interestpoints and a list of filenames to copy
		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( new HashMap< ViewId, ViewInterestPointLists >() );

		final SpimData2 newSpimData = new SpimData2(
				basePath,
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints,
				new BoundingBoxes(),
				new PointSpreadFunctions(),
				new StitchingResults(),
				new IntensityAdjustments() );

		return newSpimData;
	}
}
