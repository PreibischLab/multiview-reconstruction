/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2022 Multiview Reconstruction developers.
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
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;

import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.export.ExportTools.InstantiateViewSetupBigStitcher;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import util.Grid;

public class ExportN5API implements ImgExport
{
	public enum StorageType { N5, ZARR, HDF5 }

	public static String defaultPath = null;
	public static int defaultOption = 2;
	public static String defaultDatasetName = "fused";
	public static String defaultBaseDataset = "/";
	public static String defaultDatasetExtension = "/s0";

	public static boolean defaultBDV = false;
	public static String defaultXMLOut = null;
	public static boolean defaultManuallyAssignViewId = false;
	public static int defaultTpId = 0;
	public static int defaultVSId = 0;

	public static int defaultBlocksizeX_N5 = 128;
	public static int defaultBlocksizeY_N5 = 128;
	public static int defaultBlocksizeZ_N5 = 64;
	public static int defaultBlocksizeX_H5 = 16;
	public static int defaultBlocksizeY_H5 = 16;
	public static int defaultBlocksizeZ_H5 = 16;

	public static int defaultBlocksizeX = 0;
	public static int defaultBlocksizeY = 0;
	public static int defaultBlocksizeZ = 0;

	StorageType storageType = StorageType.values()[ defaultOption ];
	String path = defaultPath;
	String baseDataset = defaultBaseDataset;
	String datasetExtension = defaultDatasetExtension;

	boolean bdv = defaultBDV;
	String xmlOut;
	boolean manuallyAssignViewId = defaultManuallyAssignViewId;
	int tpId = defaultTpId;
	int vsId = defaultVSId;
	int splittingType;
	Map<ViewId, ViewDescription> vdMap;

	int bsX = defaultBlocksizeX_N5;
	int bsY = defaultBlocksizeY_N5;
	int bsZ = defaultBlocksizeZ_N5;

	final Compression compression = new GzipCompression( 1 );
	N5Writer driverVolumeWriter = null;

	InstantiateViewSetupBigStitcher instantiate;
	final HashMap<Integer, Integer> countViewIds = new HashMap<>();

	@Override
	public boolean finish()
	{
		driverVolumeWriter.close();
		return true;
	}

	@Override
	public int[] blocksize() { return new int[] { bsX, bsY, bsZ }; }

	@Override
	public ImgExport newInstance() { return new ExportN5API(); }

	@Override
	public String getDescription() { return "ZARR/N5/HDF5 export using N5-API"; }

	@Override
	public <T extends RealType<T> & NativeType<T>> boolean exportImage(
			RandomAccessibleInterval<T> imgInterval,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group<? extends ViewId> fusionGroup)
	{
		if ( driverVolumeWriter == null )
		{
			IOFunctions.println( "Creating " + storageType + " container '" + path + "' (assuming it doesn't already exist) ... " );

			try
			{
				if ( storageType == StorageType.N5 )
					driverVolumeWriter = new N5FSWriter(path);
				else if ( storageType == StorageType.ZARR )
					driverVolumeWriter = new N5ZarrWriter(path);
				else if ( storageType == StorageType.HDF5 )
				{
					final File dir = new File( path ).getParentFile();
					if ( !dir.exists() )
						dir.mkdirs();
					driverVolumeWriter = new N5HDF5Writer(path);
				}
				else
					throw new RuntimeException( "storageType " + storageType + " not supported." );
			}
			catch ( IOException e )
			{
				IOFunctions.println( "Couldn't create " + storageType + " container '" + path + "': " + e );
				return false;
			}
		}

		final T type = Views.iterable( imgInterval ).firstElement().createVariable();
		final DataType dataType;

		if ( UnsignedByteType.class.isInstance( type ) )
			dataType = DataType.UINT8;
		else if ( UnsignedShortType.class.isInstance( type ) && bdv && storageType == StorageType.HDF5 )
		{
			// Tobias: unfortunately I store as short and treat it as unsigned short in Java.
			// The reason is, that when I wrote this, the jhdf5 library did not support unsigned short. It's terrible and should be fixed.
			// https://github.com/bigdataviewer/bigdataviewer-core/issues/154
			// https://imagesc.zulipchat.com/#narrow/stream/327326-BigDataViewer/topic/XML.2FHDF5.20specification
			imgInterval = (RandomAccessibleInterval)Converters.convertRAI( (RandomAccessibleInterval<UnsignedShortType>)(Object)imgInterval, (i,o)->o.set( i.getShort() ), new ShortType() );
			dataType = DataType.INT16;
		}
		else if ( UnsignedShortType.class.isInstance( type ) )
			dataType = DataType.UINT16;
		else if ( FloatType.class.isInstance( type ) )
			dataType = DataType.FLOAT32;
		else
			throw new RuntimeException( "dataType " + type.getClass().getSimpleName() + " not supported." );

		final RandomAccessibleInterval< T > img = Views.zeroMin( imgInterval );

		final String dataset;
		final ViewId viewId;

		//
		// define dataset name
		//
		if ( !bdv )
		{
			viewId = null;
			dataset = new File( new File( baseDataset , title ).toString(), datasetExtension ).toString();
		}
		else
		{
			if ( manuallyAssignViewId )
				viewId = new ViewId( tpId, vsId );
			else
				viewId = getViewIdForGroup( fusionGroup, splittingType );

			dataset = ExportTools.createBDVPath( viewId, this.storageType );
		}

		//
		// create dataset
		//
		if ( driverVolumeWriter.exists( dataset ) )
		{
			IOFunctions.println( "Dataset '" + dataset + "'. STOPPING!" );
			return false;
		}

		IOFunctions.println( "Creating dataset '" + dataset + "' ... " );

		try
		{
			driverVolumeWriter.createDataset(
					dataset,
					bb.dimensionsAsLongArray(),
					blocksize(),
					dataType,
					compression );

			driverVolumeWriter.setAttribute( dataset, "min", bb.minAsLongArray() );
		}
		catch ( IOException e )
		{
			IOFunctions.println( "Couldn't create " + storageType + " container '" + path + "': " + e );
			return false;
		}

		//
		// write bdv-metadata into dataset
		//
		if ( bdv )
		{
			try
			{
				// TODO: the first time the XML does not exist, thus instantiate is not called
				ExportTools.writeBDVMetaData(
						driverVolumeWriter,
						storageType,
						dataType,
						bb.dimensionsAsLongArray(),
						compression,
						blocksize(),
						viewId,
						path,
						xmlOut,
						instantiate );
			}
			catch (SpimDataException | IOException e)
			{
				e.printStackTrace();
				IOFunctions.println( "Failed to write metadata for '" + dataset + "': " + e );
				return false;
			}
		}

		//
		// export image
		//
		final List<long[][]> grid =
				( storageType == StorageType.HDF5 ) ?
						Grid.create(
								bb.dimensionsAsLongArray(),
								new int[] {
										blocksize()[0] * 8,
										blocksize()[1] * 8,
										blocksize()[2] * 4
								},
								blocksize() ) :
						Grid.create(
								bb.dimensionsAsLongArray(),
								blocksize() );

		IOFunctions.println( "num blocks = " + Grid.create( bb.dimensionsAsLongArray(), blocksize() ).size() );
		IOFunctions.println( "num processing blocks (processing in larger chunks for HDF5) = " + grid.size() );

		final long time = System.currentTimeMillis();

		final ExecutorService ex = DeconViews.createExecutorService();

		ex.submit(() ->
			grid.parallelStream().forEach(
					gridBlock -> {
						try {

							final Interval block =
									Intervals.translate(
											new FinalInterval( gridBlock[1] ), // blocksize
											gridBlock[0] ); // block offset
	
							final RandomAccessibleInterval< T > source = Views.interval( img, block );
	
							final RandomAccessibleInterval sourceGridBlock = Views.offsetInterval(source, gridBlock[0], gridBlock[1]);
							N5Utils.saveBlock(sourceGridBlock, driverVolumeWriter, dataset, gridBlock[2]);
						}
						catch (IOException e) 
						{
							IOFunctions.println( "Error writing block offset=" + Util.printCoordinates( gridBlock[0] ) + "' ... " );
							e.printStackTrace();
						}
					} )
			);

		try
		{
			ex.shutdown();
			ex.awaitTermination( 10000000, TimeUnit.HOURS);
		}
		catch (InterruptedException e)
		{
			IOFunctions.println( "Failed to write HDF5/N5/ZARR. Error: " + e );
			e.printStackTrace();
			return false;
		}

		//System.out.println( "Saved, e.g. view with './n5-view -i " + n5Path + " -d " + n5Dataset );
		IOFunctions.println( "Saved, took: " + (System.currentTimeMillis() - time ) + " ms." );

		return true;
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion)
	{
		//
		// Initial dialog
		//
		final GenericDialogPlus gdInit = new GenericDialogPlus( "Save fused images as ZARR/N5/HDF5 using N5-API" );

		final String[] options = 
				Arrays.asList( StorageType.values() ).stream().map( s -> s.name() ).toArray(String[]::new);

		gdInit.addChoice( "Export as ...", options, options[ defaultOption ] );

		gdInit.addMessage(
				"For local export HDF5 is a reasonable format choice (unless you need a specific one)\n"
				+ "since it supports small blocksizes, can be written multi-threaded, and produces a single file.\n\n"
				+ "For cluster/cloud - distributed fusion please check out BigStitcher-Spark.", GUIHelper.smallStatusFont, GUIHelper.neutral );

		gdInit.addMessage(
				"Note: you can always add new datasets to an existing HDF5/N5/ZARR container, so you can specify\n"
				+ "existing N5/ZARR-directories or HDF5-files. If a dataset inside a container already exists\n"
				+ "export will stop and NOT overwrite existing datasets.", GUIHelper.smallStatusFont, GUIHelper.neutral );

		gdInit.addCheckbox( "Create a BDV/BigStitcher compatible export (HDF5/N5 are supported)", defaultBDV );

		gdInit.addMessage(
				"Note: if you selected a single image to fuse (e.g. all tiles of one channel), you can manually\n"
				+ "specify the ViewId it will be assigned in the following dialog. This is useful if you want to\n"
				+ "add the fused image to an existing dataset so you can specify a ViewId that does not exist yet.",
				GUIHelper.smallStatusFont, GUIHelper.neutral );

		gdInit.showDialog();
		if ( gdInit.wasCanceled() )
			return false;

		final int previousExportOption = defaultOption;
		this.storageType = StorageType.values()[ defaultOption = gdInit.getNextChoiceIndex() ];
		this.bdv = defaultBDV = gdInit.getNextBoolean();
		this.splittingType = fusion.getSplittingType();
		this.instantiate = new InstantiateViewSetupBigStitcher( splittingType );

		final String name = storageType.name();
		final String ext;

		if ( storageType == StorageType.HDF5 )
			ext = ".h5";
		else if ( storageType == StorageType.N5 )
			ext = ".n5";
		else
			ext = ".zarr";

		if ( bdv && storageType == StorageType.ZARR )
		{
			IOFunctions.println( "BDV-compatible ZARR file not (yet) supported." );
			return false;
		}

		//
		// next dialog
		//
		final GenericDialogPlus gd = new GenericDialogPlus( "Export " + name +" using N5-API" );

		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = fusion.getSpimData().getBasePath().getAbsolutePath();

			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );
			
			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );

			defaultPath = new File( defaultPath, defaultDatasetName + "/" + defaultDatasetName+ext ).getAbsolutePath();
		}

		if ( storageType == StorageType.HDF5 )
			PluginHelper.addSaveAsFileField( gd, name + "_file (should end with "+ext+")", defaultPath, 80 );
		else
			PluginHelper.addSaveAsDirectoryField( gd, name + "_dataset_path (should end with "+ext+")", defaultPath, 80 );

		if ( bdv )
		{
			if ( defaultXMLOut == null )
			{
				defaultXMLOut = fusion.getSpimData().getBasePath().getAbsolutePath();

				if ( defaultXMLOut.endsWith( "/." ) )
					defaultXMLOut = defaultXMLOut.substring( 0, defaultXMLOut.length() - 1 );

				if ( defaultXMLOut.endsWith( "/./" ) )
					defaultXMLOut = defaultXMLOut.substring( 0, defaultXMLOut.length() - 2 );

				defaultXMLOut = new File( defaultXMLOut, defaultDatasetName + "/dataset.xml" ).toString();
			}

			PluginHelper.addSaveAsFileField( gd, "XML_output_file", defaultXMLOut, 80 );

			if ( fusion.getFusionGroups().size() == 1 )
			{
				gd.addCheckbox( "Manually_define_ViewId", defaultManuallyAssignViewId );
				gd.addNumericField( "ViewId_TimepointId", defaultTpId);
				gd.addNumericField( "ViewId_SetupId", defaultVSId);
				gd.addMessage( "" );
			}
		}
		else
		{
			gd.addStringField( name + "_base_dataset", defaultBaseDataset );
			gd.addStringField( name + "_dataset_extension", defaultDatasetExtension );
	
			gd.addMessage(
					"Note: Data inside the HDF5/N5/ZARR container are stored in datasets (similar to a filesystem).\n"
					+ "Each fused volume will be named according to its content (e.g. fused_tp0_ch2) and become a\n"
					+ "dataset inside the 'base dataset'. You can add a dataset extension for each volume,\n"
					+ "e.g. /base/fused_tp0_ch2/s0, where 's0' suggests it is full resolution.", GUIHelper.smallStatusFont, GUIHelper.neutral );
		}

		// export type changed or undefined
		if ( defaultBlocksizeX <= 0 || storageType.ordinal() != previousExportOption )
		{
			if ( storageType == StorageType.HDF5 )
			{
				defaultBlocksizeX = defaultBlocksizeX_H5;
				defaultBlocksizeY = defaultBlocksizeY_H5;
				defaultBlocksizeZ = defaultBlocksizeZ_H5;
			}
			else
			{
				defaultBlocksizeX = defaultBlocksizeX_N5;
				defaultBlocksizeY = defaultBlocksizeY_N5;
				defaultBlocksizeZ = defaultBlocksizeZ_N5;
			}
		}

		gd.addNumericField( "block_size_x", defaultBlocksizeX, 0);
		gd.addNumericField( "block_size_y", defaultBlocksizeY, 0);
		gd.addNumericField( "block_size_z", defaultBlocksizeZ, 0);

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		this.path = defaultPath = gd.getNextString().trim();

		if ( bdv )
		{
			this.xmlOut = defaultXMLOut = gd.getNextString();

			if ( fusion.getFusionGroups().size() == 1 )
			{
				this.manuallyAssignViewId = gd.getNextBoolean();
				this.tpId = (int)Math.round( gd.getNextNumber() );
				this.vsId = (int)Math.round( gd.getNextNumber() );
			}
			else
			{
				// depends on fusion group, defined during export
				// ExportTools.createBDVPath( this.bdvString, this.storageType );
			}
		}
		else
		{
			this.baseDataset = defaultBaseDataset  = gd.getNextString().trim();
			this.datasetExtension = defaultDatasetExtension = gd.getNextString().trim();
		}

		bsX = defaultBlocksizeX = (int)Math.round( gd.getNextNumber() );
		bsY = defaultBlocksizeY = (int)Math.round( gd.getNextNumber() );
		bsZ = defaultBlocksizeZ = (int)Math.round( gd.getNextNumber() );

		return true;
	}

	private ViewId getViewIdForGroup(
			final Group< ? extends ViewId > group,
			final int splittingType )
	{
		// 0 == "Each timepoint &amp; channel",
		// 1 == "Each timepoint, channel &amp; illumination",
		// 2 == "All views together",
		// 3 == "Each view"

		final ViewId vd = group.getViews().iterator().next();
		final int tpId, vsId;

		if ( splittingType == 0 || splittingType == 1 || splittingType == 3 )
		{
			tpId = vd.getTimePointId();

			if ( countViewIds.containsKey( tpId ) )
				vsId = countViewIds.get( tpId ) + 1;
			else
				vsId = 0;

			countViewIds.put( tpId, vsId );
		}
		else if ( splittingType == 2 )
		{
			tpId = 0;
			vsId = 0;

			// even though this is unnecessary
			countViewIds.put( tpId, vsId );
		}
		else
		{
			IOFunctions.println( "SplittingType " + splittingType + " unknown. Stopping.");
			return null;
		}

		return new ViewId(tpId, vsId);
	}
}
