/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.export.ImgExport;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Fusion implements PlugIn
{
	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		fuse( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean fuse(
			final SpimData2 spimData,
			final List< ViewId > viewsToProcess )
	{
		final FusionGUI fusion = new FusionGUI( spimData, viewsToProcess );

		if ( !fusion.queryDetails() )
			return false;

		final List< Group< ViewDescription > > groups = fusion.getFusionGroups();
		int i = 0;

		if ( fusion.preserveAnisotropy() )
		{
			final double anisoF = fusion.getAnisotropyFactor();

			Interval bb = fusion.getBoundingBox();
			final long[] min = new long[ 3 ];
			final long[] max = new long[ 3 ];

			bb.min( min );
			bb.max( max );

			min[ 2 ] = Math.round( Math.floor( min[ 2 ] / anisoF ) );
			max[ 2 ] = Math.round( Math.ceil( max[ 2 ] / anisoF ) );

			final Interval boundingBox = new FinalInterval( min, max );

			// we need to update the bounding box here
			fusion.setBoundingBox( boundingBox );
		}

		// query exporter parameters
		final ImgExport exporter = fusion.getNewExporterInstance();

		// query exporter parameters
		if ( !exporter.queryParameters( fusion ) )
			return false;

		for ( final Group< ViewDescription > group : groups )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing group " + (++i) + "/" + groups.size() + " (group=" + group + ")" );

			for ( final ViewDescription vd : group )
				System.out.println( Group.pvid( vd ) );
			final Interval boundingBox = fusion.getBoundingBox();
			final double anisoF = fusion.preserveAnisotropy() ? fusion.getAnisotropyFactor() : Double.NaN;

			final double downsampling = fusion.getDownsampling();

			final RandomAccessibleInterval< FloatType > virtual;

			if ( !fusion.preserveAnisotropy() )
			{
				virtual = FusionTools.fuseVirtual(
					spimData,
					group.getViews(),
					fusion.useBlending(),
					fusion.useContentBased(),
					fusion.getInterpolation(),
					boundingBox,
					downsampling );
			}
			else
			{
				final ImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();

				final HashMap< ViewId, AffineTransform3D > registrations = new HashMap<>();

				for ( final ViewId viewId : group.getViews() )
				{
					final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
					vr.updateModel();
					final AffineTransform3D model = vr.getModel().copy();
					final AffineTransform3D aniso = new AffineTransform3D();
					aniso.set(
							1.0, 0.0, 0.0, 0.0,
							0.0, 1.0, 0.0, 0.0,
							0.0, 0.0, 1.0/anisoF, 0.0 );
					model.preConcatenate( aniso );
					registrations.put( viewId, model );
				}

				final Map< ViewId, ViewDescription > viewDescriptions = spimData.getSequenceDescription().getViewDescriptions();

				virtual = FusionTools.fuseVirtual(
						imgLoader,
						registrations,
						viewDescriptions,
						group.getViews(),
						fusion.useBlending(),
						fusion.useContentBased(),
						fusion.getInterpolation(),
						boundingBox,
						downsampling );
			}

			if ( fusion.getPixelType() == 1 ) // 16 bit
			{
				final double[] minmax = determineInputBitDepth( group, spimData, virtual );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Range for conversion to 16-bit, min=" + minmax[ 0 ] + ", max=" + minmax[ 1 ] );

				if ( !cacheAndExport(
						new ConvertedRandomAccessibleInterval< FloatType, UnsignedShortType >(
								virtual, new RealUnsignedShortConverter<>( minmax[ 0 ], minmax[ 1 ] ), new UnsignedShortType() ),
						new UnsignedShortType(), fusion, exporter, group, minmax ) )
					return false;
			}
			else
			{
				if ( !cacheAndExport( virtual, new FloatType(), fusion, exporter, group, null ) )
					return false;
			}
		}

		exporter.finish();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): DONE." );

		return true;
	}

	public static double[] determineInputBitDepth( final Group< ViewDescription > group, final SpimData2 spimData, final RandomAccessibleInterval< FloatType > virtual )
	{
		SetupImgLoader< ? > loader = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( group.iterator().next().getViewSetupId() );
		Object type = loader.getImageType();

		if ( UnsignedByteType.class.isInstance( type ) )
			return new double[] { 0, 255 };
		else if ( UnsignedShortType.class.isInstance( type ) )
			return new double[] { 0, 65535 };
		else
		{
			IOFunctions.println( "WARNING: You are saving a non-8/16 bit input as 16bit, have to manually determine min/max of the fused image." );

			final float[] minmax = FusionTools.minMax( virtual );
			return new double[]{ minmax[ 0 ], minmax[ 1 ] };
		}
	}

	protected static < T extends RealType< T > & NativeType< T > > boolean cacheAndExport(
			final RandomAccessibleInterval< T > output,
			final T type,
			final FusionGUI fusion,
			final ImgExport exporter,
			final Group< ViewDescription > group,
			final double[] minmax )
	{
		final RandomAccessibleInterval< T > processedOutput;

		if ( fusion.getCacheType() == 0 ) // Virtual
			processedOutput = output;
		else if ( fusion.getCacheType() == 1 ) // Cached
			processedOutput = FusionTools.cacheRandomAccessibleInterval( output, FusionGUI.maxCacheSize, type, FusionGUI.cellDim );
		else // Precomputed
			processedOutput = FusionTools.copyImg( output, new ImagePlusImgFactory< T >(), type, true );

		final String title = getTitle( fusion.getSplittingType(), group );

		if ( minmax == null )
			return exporter.exportImage( processedOutput, fusion.getBoundingBox(), fusion.getDownsampling(), title, group );
		else
			return exporter.exportImage( processedOutput, fusion.getBoundingBox(), fusion.getDownsampling(), title, group, minmax[ 0 ], minmax[ 1 ] );
	}

	public static String getTitle( final int splittingType, final Group< ViewDescription > group )
	{
		String title;
		final ViewDescription vd0 = group.iterator().next();

		if ( splittingType == 0 ) // "Each timepoint & channel"
			title = "fused_tp_" + vd0.getTimePointId() + "_ch_" + vd0.getViewSetup().getChannel().getId();
		else if ( splittingType == 1 ) // "Each timepoint, channel & illumination"
			title = "fused_tp_" + vd0.getTimePointId() + "_ch_" + vd0.getViewSetup().getChannel().getId() + "_illum_" + vd0.getViewSetup().getIllumination().getId();
		else if ( splittingType == 2 ) // "All views together"
			title = "fused";
		else // "All views"
			title = "fused_tp_" + vd0.getTimePointId() + "_vs_" + vd0.getViewSetupId();

		return title;
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Image_Fusion().run( null );
	}
}
