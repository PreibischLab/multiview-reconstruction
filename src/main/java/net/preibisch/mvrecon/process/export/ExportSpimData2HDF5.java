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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.ProgressWriterIJ;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ExportSpimData2HDF5 implements ImgExport
{
	private FusionExportInterface fusion;

	private List< TimePoint > newTimepoints;

	private List< ViewSetup > newViewSetups;

	private Parameters params;

	private SpimData2 spimData;

	private Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo;

	private HashMap< ViewId, Partition > viewIdToPartition;

	private final ProgressWriter progressWriter = new ProgressWriterIJ();

	@Override
	public boolean finish()
	{
		System.out.println( "finish()" );
		String path = params.getSeqFile().getAbsolutePath();
		try
		{
			new XmlIoSpimData2( "" ).save( spimData, path );

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + path + "'." );

			// this spimdata object was not modified, we just wrote a new one
			return false;
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + path + "'." );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		this.fusion = fusion;

		// define new timepoints and viewsetups
		final Pair< List< TimePoint >, List< ViewSetup > > newStructure = ExportSpimData2TIFF.defineNewViewSetups( fusion, fusion.getDownsampling(), fusion.getAnisotropyFactor() );
		this.newTimepoints = newStructure.getA();
		this.newViewSetups = newStructure.getB();

		System.out.println( this + " " + fusion );

		perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( newViewSetups );

		String fn = LoadParseQueryXML.defaultXMLfilename;
		if ( fn.endsWith( ".xml" ) )
			fn = fn.substring( 0, fn.length() - ".xml".length() );
		for ( int i = 0;; ++i )
		{
			Generic_Resave_HDF5.lastExportPath = String.format( "%s-f%d.xml", fn, i );
			if ( !new File( Generic_Resave_HDF5.lastExportPath ).exists() )
				break;
		}

		boolean is16bit = fusion.getPixelType() == 1;

		final int firstviewSetupId = newViewSetups.get( 0 ).getId();
		params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, getDescription(), is16bit );

		if ( params == null )
		{
			System.out.println( "abort " );
			return false;
		}

		Pair< SpimData2, HashMap< ViewId, Partition > > init = initSpimData( newTimepoints, newViewSetups, params, perSetupExportMipmapInfo );
		this.spimData = init.getA();
		viewIdToPartition = init.getB();

		return true;
	}

	protected static Pair< SpimData2, HashMap< ViewId, Partition > > initSpimData(
			final List< TimePoint > newTimepoints,
			final List< ViewSetup > newViewSetups,
			final Parameters params,
			final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo )
	{
		// SequenceDescription containing the subset of viewsetups and timepoints. Does not have an ImgLoader yet.
		final SequenceDescription seq = new SequenceDescription( new TimePoints( newTimepoints ), newViewSetups, null, null );

		// Create identity ViewRegistration for all views.
		final Map< ViewId, ViewRegistration > regMap = new HashMap< ViewId, ViewRegistration >();
		for ( final ViewDescription vDesc : seq.getViewDescriptions().values() )
			regMap.put( vDesc, new ViewRegistration( vDesc.getTimePointId(), vDesc.getViewSetupId() ) );
		final ViewRegistrations viewRegistrations = new ViewRegistrations( regMap );

		// Create empty ViewInterestPoints.
		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( new HashMap< ViewId, ViewInterestPointLists >() );

		// base path is directory containing the XML file.
		File basePath = params.getSeqFile().getParentFile();

		ArrayList< Partition > hdf5Partitions = null;
		HashMap< ViewId, Partition > viewIdToPartition = new HashMap< ViewId, Partition >();

		if ( params.getSplit() )
		{
			String basename = params.getHDF5File().getAbsolutePath();
			if ( basename.endsWith( ".h5" ) )
				basename = basename.substring( 0, basename.length() - ".h5".length() );
		    hdf5Partitions = Partition.split( newTimepoints, newViewSetups, params.getTimepointsPerPartition(), params.getSetupsPerPartition(), basename );
			for ( final ViewDescription vDesc : seq.getViewDescriptions().values() )
				for ( Partition p : hdf5Partitions )
					if ( p.contains( vDesc ) )
					{
						viewIdToPartition.put( vDesc, p );
						break;
					}
			WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, hdf5Partitions, params.getHDF5File() );
		}
		else
		{
			final HashMap< Integer, Integer > timepointIdSequenceToPartition = new HashMap< Integer, Integer >();
			for ( final TimePoint timepoint : newTimepoints )
				timepointIdSequenceToPartition.put( timepoint.getId(), timepoint.getId() );
			final HashMap< Integer, Integer > setupIdSequenceToPartition = new HashMap< Integer, Integer >();
			for ( final ViewSetup setup : newViewSetups )
				setupIdSequenceToPartition.put( setup.getId(), setup.getId() );
			final Partition partition = new Partition( params.getHDF5File().getAbsolutePath(), timepointIdSequenceToPartition, setupIdSequenceToPartition );
			for ( final ViewDescription vDesc : seq.getViewDescriptions().values() )
				viewIdToPartition.put( vDesc, partition );
		}

		seq.setImgLoader( new Hdf5ImageLoader( params.getHDF5File(), hdf5Partitions, seq, false ) );
		SpimData2 spimData = new SpimData2( basePath, seq, viewRegistrations, viewsInterestPoints, new BoundingBoxes(), new PointSpreadFunctions(), new StitchingResults(), new IntensityAdjustments() );

		return new ValuePair< SpimData2, HashMap<ViewId,Partition> >( spimData, viewIdToPartition );
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

	public static < T extends RealType< T > > double[] updateAndGetMinMax( final RandomAccessibleInterval< T > img, final Parameters params )
	{
		double min, max;

		if ( params == null || params.getConvertChoice() == 0 || Double.isNaN( params.getMin() ) || Double.isNaN( params.getMin() ) )
		{
			final float[] minmax = FusionTools.minMax( img );
			min = minmax[ 0 ];
			max = minmax[ 1 ];

			min = Math.max( 0, min - ((min+max)/2.0) * 0.1 );
			max = max + ((min+max)/2.0) * 0.1;

			if ( params != null )
			{
				params.setMin( min );
				params.setMax( max );
			}
		}
		else
		{
			min = params.getMin();
			max = params.getMax();
		}

		IOFunctions.println( "Min intensity for 16bit conversion: " + min );
		IOFunctions.println( "Max intensity for 16bit conversion: " + max );

		return new double[]{ min, max };
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< UnsignedShortType > convert( final RandomAccessibleInterval< T > img, final Parameters params )
	{
		final double[] minmax = updateAndGetMinMax( img, params );

		final RealUnsignedShortConverter< T > converter = new RealUnsignedShortConverter< T >( minmax[ 0 ], minmax[ 1 ] );

		return new ConvertedRandomAccessibleInterval<T, UnsignedShortType>( img, converter, new UnsignedShortType() );
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			double min, double max )
	{
		System.out.println( "exportImage2()" );

		final ViewId newViewId = ExportSpimData2TIFF.identifyNewViewId( newTimepoints, newViewSetups, fusionGroup, fusion );

		// write the image
		final RandomAccessibleInterval< UnsignedShortType > ushortimg;
		if ( ! UnsignedShortType.class.isInstance( Util.getTypeFromInterval( img ) ) )
			ushortimg = convert( img, params );
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
		System.out.println( "newInstance()" );
		return new ExportSpimData2HDF5();
	}

	@Override
	public String getDescription()
	{
		System.out.println( "getDescription()" );
		return "Save as new XML Project (HDF5)";
	}

}
