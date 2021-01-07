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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.tools.MergePartitionList;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
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
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.ProgressWriterIJ;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class AppendSpimData2HDF5 implements ImgExport
{
	public static String defaultPath = null;

	private FusionExportInterface fusion;

	private List< TimePoint > newTimepoints;

	private List< ViewSetup > newViewSetups;

	private Parameters params;

	private SpimData2 spimData;

	private SpimData2 fusionOnlySpimData;

	private Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo;

	private HashMap< ViewId, Partition > viewIdToPartition;

	private final ProgressWriter progressWriter = new ProgressWriterIJ();

	@Override
	public boolean finish()
	{
		// this spimdata object was modified
		return true;
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		System.out.println( "queryParameters()" );

		this.fusion = fusion;

		// define new timepoints and viewsetups
		final Pair< List< TimePoint >, List< ViewSetup > > newStructure = defineNewViewSetups( fusion );
		this.newTimepoints = newStructure.getA();
		this.newViewSetups = newStructure.getB();

		this.spimData = (SpimData2) fusion.getSpimData();

		Hdf5ImageLoader il;
		if (Hdf5ImageLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ))
			il = ( Hdf5ImageLoader ) spimData.getSequenceDescription().getImgLoader();
		else
			return false;

		perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( newViewSetups );

		String fn = il.getHdf5File().getAbsolutePath();
		if ( fn.endsWith( ".h5" ) )
			fn = fn.substring( 0, fn.length() - ".h5".length() );
		String fusionHdfFilename = "";
		String fusionXmlFilename = "";
		for ( int i = 0;; ++i )
		{
			fusionHdfFilename = String.format( "%s-f%d.h5", fn, i );
			fusionXmlFilename = String.format( "%s-f%d.xml", fn, i );
			if ( !new File( fusionHdfFilename ).exists() && !new File( fusionXmlFilename ).exists() )
				break;
		}

		boolean is16bit = fusion.getPixelType() == 1;

		final int firstviewSetupId = newViewSetups.get( 0 ).getId();
		params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), false, getDescription(), is16bit );
		if ( params == null )
		{
			System.out.println( "abort " );
			return false;
		}
		params.setHDF5File( new File( fusionHdfFilename ) );
		params.setSeqFile( new File( fusionXmlFilename ) );

		Pair< SpimData2, HashMap< ViewId, Partition > > init = ExportSpimData2HDF5.initSpimData(
				newTimepoints, newViewSetups, params, perSetupExportMipmapInfo );
		fusionOnlySpimData = init.getA();
		viewIdToPartition = init.getB();

		perSetupExportMipmapInfo.putAll(
				MergePartitionList.getHdf5PerSetupExportMipmapInfos( spimData.getSequenceDescription() ) );

		appendSpimData2( spimData, newTimepoints, newViewSetups );

		ArrayList< Partition > mergedPartitions = MergePartitionList.getMergedHdf5PartitionList(
				spimData.getSequenceDescription(), fusionOnlySpimData.getSequenceDescription() );

		String mergedHdfFilename = "";
		for ( int i = 0;; ++i )
		{
			mergedHdfFilename = String.format( "%s-m%d.h5", fn, i );
			if ( !new File( mergedHdfFilename ).exists() )
				break;
		}

		SequenceDescription seq = spimData.getSequenceDescription();
		Hdf5ImageLoader newLoader = new Hdf5ImageLoader(
				new File( mergedHdfFilename ), mergedPartitions, seq, false );
		seq.setImgLoader( newLoader );
		WriteSequenceToHdf5.writeHdf5PartitionLinkFile( spimData.getSequenceDescription(), perSetupExportMipmapInfo );

		return true;
	}

	@Override
	public < T extends RealType< T > & NativeType< T >> boolean exportImage(
			RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup )
	{
		System.out.println( "exportImage1()" );
		return exportImage( img, bb, downsampling, anisoF, title, fusionGroup, Double.NaN, Double.NaN );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			double min,
			double max )
	{
		System.out.println( "exportImage2()" );

		final ViewId newViewId = ExportSpimData2TIFF.identifyNewViewId( newTimepoints, newViewSetups, fusionGroup, fusion );

		// write the image
		final RandomAccessibleInterval< UnsignedShortType > ushortimg;
		if ( ! UnsignedShortType.class.isInstance( Util.getTypeFromInterval( img ) ) )
			ushortimg = ExportSpimData2HDF5.convert( img, params );
		else
			ushortimg = ( RandomAccessibleInterval ) img;

		final Partition partition = viewIdToPartition.get( newViewId );
		final ExportMipmapInfo mipmapInfo = perSetupExportMipmapInfo.get( newViewId.getViewSetupId() );
		final boolean writeMipmapInfo = true; // TODO: remember whether we already wrote it and write only once
		final boolean deflate = params.getDeflate();
		final ProgressWriter progressWriter = new SubTaskProgressWriter( this.progressWriter, 0.0, 1.0 ); // TODO
		WriteSequenceToHdf5.writeViewToHdf5PartitionFile( ushortimg, partition, newViewId.getTimePointId(), newViewId.getViewSetupId(), mipmapInfo, writeMipmapInfo, deflate, null, null, Threads.numThreads(), progressWriter );

		// update the registrations
		final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( newViewId );

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
	public ImgExport newInstance()
	{
		//BoundingBoxGUI.defaultPixelType = 1; // set to 16 bit by default
		return new AppendSpimData2HDF5();
	}

	@Override
	public String getDescription()
	{
		return "Append to current XML Project (HDF5)";
	}

	public static Pair< List< TimePoint >, List< ViewSetup > > defineNewViewSetups( final FusionExportInterface fusion )
	{
		final List< ViewSetup > newViewSetups = new ArrayList<>();
		final List< TimePoint > newTimepoints;

		int newViewSetupId = Integer.MIN_VALUE;
		int newTileId = Integer.MIN_VALUE;
		int newChannelId = Integer.MIN_VALUE;
		int newAngleId = Integer.MIN_VALUE;
		int newIllumId = Integer.MIN_VALUE;
		int newTimepointId = Integer.MIN_VALUE;

		for( final ViewSetup vs : fusion.getSpimData().getSequenceDescription().getViewSetupsOrdered() )
		{
			newViewSetupId = Math.max( vs.getId(), newViewSetupId );
			newTileId = Math.max( vs.getTile().getId(), newTileId );
			newChannelId = Math.max( vs.getChannel().getId(), newChannelId );
			newAngleId = Math.max( vs.getAngle().getId(), newAngleId );
			newIllumId = Math.max( vs.getIllumination().getId(), newIllumId );
		}

		for ( final TimePoint tp : fusion.getSpimData().getSequenceDescription().getTimePoints().getTimePointsOrdered() )
			newTimepointId = Math.max( tp.getId(), newTimepointId );

		// this is the id we start with
		++newTimepointId;
		++newViewSetupId;
		++newTileId;
		++newChannelId;
		++newAngleId;
		++newIllumId;

		final double downsampling = Double.isNaN( fusion.getDownsampling() ) ? 1.0 : fusion.getDownsampling();

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
							new FinalVoxelDimensions( "px", Util.getArrayFromValue( downsampling, 3 ) ),
							new Tile( newTileId ),
							c,
							new Angle( newAngleId ),
							new Illumination( newIllumId ) ) );
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
									new FinalVoxelDimensions( "px", Util.getArrayFromValue( downsampling, 3 ) ),
									new Tile( newTileId ),
									channels.get( newChannelId ),
									new Angle( newAngleId ),
									illums.get( i ) ) );
			}
		}
		else if ( fusion.getSplittingType() == 2 ) // "All views together"
		{
			newTimepoints = new ArrayList<>();
			newTimepoints.add( new TimePoint( newTimepointId ) );

			newViewSetups.add(
					new ViewSetup(
							newViewSetupId++,
							"Fused",
							fusion.getDownsampledBoundingBox(),
							new FinalVoxelDimensions( "px", Util.getArrayFromValue( downsampling, 3 ) ),
							new Tile( newTileId ),
							new Channel( newChannelId ),
							new Angle( newAngleId ),
							new Illumination( newIllumId ) ) );
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
								newViewSetupId++,
								vs.getName(),
								fusion.getDownsampledBoundingBox(),
								new FinalVoxelDimensions( "px", Util.getArrayFromValue( downsampling, 3 ) ),
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

	/*
	 * Assembles a new SpimData2 based on the timepoints and viewsetups.
	 * The imgloader is still not set here.
	 *
	 */
	public static void appendSpimData2(
			final SpimData2 spimData,
			final List< TimePoint > timepointsToProcess,
			final List< ViewSetup > newViewSetups )
	{
		final SequenceDescription sequenceDescription = spimData.getSequenceDescription();

		// current viewsetups
		final Map< Integer, ViewSetup > viewSetups = (Map< Integer, ViewSetup >)sequenceDescription.getViewSetups();

		// add all the newly fused viewsetups
		for ( final ViewSetup vs : newViewSetups )
			viewSetups.put( vs.getId(), vs );

		resetViewSetupsAndDescriptions( sequenceDescription );

		// all the timepoints that are not processed are missing views
		final HashSet< ViewId > newMissingViews = new HashSet< ViewId >();
		final Map< ViewId, ViewInterestPointLists > ips = spimData.getViewInterestPoints().getViewInterestPoints();
		for ( final TimePoint t : spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
		{
			if ( !timepointsToProcess.contains( t ) )
			{
				for ( final ViewSetup newSetup : newViewSetups )
					newMissingViews.add( new ViewId( t.getId(), newSetup.getId() ) );
			}
			else
			{
				for ( final ViewSetup newSetup : newViewSetups )
					ips.put( new ViewId( t.getId(), newSetup.getId() ), new ViewInterestPointLists( t.getId(), newSetup.getId() ) );
			}
		}

		// are there new ones? if so extend the maybe not existant list
		if ( newMissingViews.size() > 0 )
		{
			MissingViews m = spimData.getSequenceDescription().getMissingViews();
			if ( m != null )
				newMissingViews.addAll( m.getMissingViews() );
			m = new MissingViews( newMissingViews );

			// marking the missing views
			setMissingViews( spimData.getSequenceDescription(), m );
			BasicViewDescription.markMissingViews( spimData.getSequenceDescription().getViewDescriptions(), m );
		}

		// add the viewregistrations to the exisiting ones
		final Map< ViewId, ViewRegistration > regMap = spimData.getViewRegistrations().getViewRegistrations();

		for ( final TimePoint tp : timepointsToProcess )
			for ( final ViewSetup vs : newViewSetups )
			{
				final ViewDescription vd = sequenceDescription.getViewDescription( tp.getId(), vs.getId() );
				final ViewRegistration viewRegistration = new ViewRegistration( vd.getTimePointId(), vd.getViewSetupId() );
				viewRegistration.identity();
				regMap.put( vd, viewRegistration );
			}
	}

	private static final void resetViewSetupsAndDescriptions( final SequenceDescription s )
	{
		try
		{
			Class< ? > clazz = null;
			Field viewSetupsOrderedDirty = null;
			Field viewDescriptionsDirty = null;

			do
			{
				if ( clazz == null )
					clazz = s.getClass();
				else
					clazz = clazz.getSuperclass();

				if ( clazz != null )
					for ( final Field field : clazz.getDeclaredFields() )
					{
						if ( field.getName().equals( "viewSetupsOrderedDirty" ) )
							viewSetupsOrderedDirty = field;

						if ( field.getName().equals( "viewDescriptionsDirty" ) )
							viewDescriptionsDirty = field;
					}
			}
			while ( ( viewSetupsOrderedDirty == null || viewDescriptionsDirty == null ) && clazz != null );

			if ( viewDescriptionsDirty == null || viewDescriptionsDirty == null )
			{
				System.out.println( "Failed to find SequenceDescription.viewSetupsOrderedDirty or SequenceDescription.viewDescriptionsDirty field. Quiting." );
				return;
			}

			viewSetupsOrderedDirty.setAccessible( true );
			viewSetupsOrderedDirty.set( s, true );
			viewDescriptionsDirty.setAccessible( true );
			viewDescriptionsDirty.set( s, true );
		}
		catch ( Exception e ) { e.printStackTrace(); }
	}

	private static final void setMissingViews( final SequenceDescription s, final MissingViews m )
	{
		try
		{
			Class< ? > clazz = null;
			boolean found = false;

			do
			{
				if ( clazz == null )
					clazz = s.getClass();
				else
					clazz = clazz.getSuperclass();

				if ( clazz != null )
					for ( final Method method : clazz.getDeclaredMethods() )
						if ( method.getName().equals( "setMissingViews" ) )
							found = true;
			}
			while ( !found && clazz != null );

			if ( !found )
			{
				System.out.println( "Failed to find SequenceDescription.setMissingViews method. Quiting." );
				return;
			}

			final Method setMissingViews = clazz.getDeclaredMethod( "setMissingViews", MissingViews.class );
			setMissingViews.setAccessible( true );
			setMissingViews.invoke( s, m );
		}
		catch ( Exception e ) { e.printStackTrace(); }
	}
}
