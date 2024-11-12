/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2024 Multiview Reconstruction developers.
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
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory.StorageFormat;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.plugin.util.PluginHelper;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.n5api.N5ApiTools;
import net.preibisch.mvrecon.process.n5api.N5ApiTools.MultiResolutionLevelInfo;
import net.preibisch.mvrecon.process.n5api.SpimData2Tools;
import net.preibisch.mvrecon.process.n5api.SpimData2Tools.InstantiateViewSetupBigStitcher;
import util.Grid;
import util.URITools;

public class ExportN5Api implements ImgExport
{
	public static String defaultPathURI = null;
	public static int defaultOption = 1;
	public static String defaultDatasetName = "fused";
	public static String defaultBaseDataset = "/";
	public static String defaultDatasetExtension = "/s0";

	public static boolean defaultBDV = false;
	public static boolean defaultMultiRes = false;
	public static String defaultXMLOutURI = null;
	public static boolean defaultManuallyAssignViewId = false;
	public static int defaultTpId = 0;
	public static int defaultVSId = 0;

	public static int defaultBlocksizeX_N5 = 128;
	public static int defaultBlocksizeY_N5 = 128;
	public static int defaultBlocksizeZ_N5 = 64;
	public static int defaultBlocksizeX_H5 = 32;
	public static int defaultBlocksizeY_H5 = 32;
	public static int defaultBlocksizeZ_H5 = 16;

	public static boolean defaultAdvancedBlockSize = false;

	public static int defaultBlocksizeFactorX_N5 = 1;
	public static int defaultBlocksizeFactorY_N5 = 1;
	public static int defaultBlocksizeFactorZ_N5 = 1;
	public static int defaultBlocksizeFactorX_H5 = 4;
	public static int defaultBlocksizeFactorY_H5 = 4;
	public static int defaultBlocksizeFactorZ_H5 = 4;

	StorageFormat storageType = StorageFormat.values()[ defaultOption ];
	URI path = (defaultPathURI != null && defaultPathURI.trim().length() > 0 ) ? URI.create( defaultPathURI ) : null;
	String baseDataset = defaultBaseDataset;
	String datasetExtension = defaultDatasetExtension;

	boolean bdv = defaultBDV;
	URI xmlOut;
	boolean manuallyAssignViewId = false;
	int tpId = defaultTpId;
	int vsId = defaultVSId;
	int splittingType;
	Map<ViewId, ViewDescription> vdMap;

	int[][] downsampling = null; //if downsampling is desired

	int bsX = defaultBlocksizeX_N5;
	int bsY = defaultBlocksizeY_N5;
	int bsZ = defaultBlocksizeZ_N5;

	int bsFactorX = defaultBlocksizeFactorX_N5;
	int bsFactorY = defaultBlocksizeFactorY_N5;
	int bsFactorZ = defaultBlocksizeFactorZ_N5;

	Compression compression = null;
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

	public int[] computeBlocksizeFactor() { return new int[] { bsFactorX, bsFactorY, bsFactorZ }; }

	@Override
	public ImgExport newInstance() { return new ExportN5Api(); }

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
				if ( storageType == StorageFormat.HDF5 )
				{
					final File dir = new File( URITools.fromURI( path ) ).getParentFile();
					if ( !dir.exists() )
						dir.mkdirs();
					driverVolumeWriter = new N5HDF5Writer( URITools.fromURI( path ) );
				}
				else if ( storageType == StorageFormat.N5 || storageType == StorageFormat.ZARR )
				{
					driverVolumeWriter = URITools.instantiateN5Writer( storageType, path );
				}
				else
					throw new RuntimeException( "storageType " + storageType + " not supported." );
			}
			catch ( Exception e )
			{
				IOFunctions.println( "Couldn't create " + storageType + " container '" + path + "': " + e );
				return false;
			}
		}

		final T type = imgInterval.getType();
		final DataType dataType = N5Utils.dataType( type );
		final EnumSet< DataType > supportedDataTypes = EnumSet.of( DataType.UINT8, DataType.UINT16, DataType.FLOAT32 );
		if ( !supportedDataTypes.contains( dataType ) )
			throw new RuntimeException( "dataType " + type.getClass().getSimpleName() + " not supported." );

		final RandomAccessibleInterval< T > img = Views.zeroMin( imgInterval );

		final MultiResolutionLevelInfo[] mrInfo;

		if ( bdv )
		{
			final ViewId viewId;

			if ( manuallyAssignViewId )
				viewId = new ViewId( tpId, vsId );
			else
				viewId = getViewIdForGroup( fusionGroup, splittingType );

			IOFunctions.println( "Assigning ViewId " + Group.pvid( viewId ) );

			try
			{
				// create or extend XML, setup s0 and multiresolution pyramid
				mrInfo = SpimData2Tools.writeBDVMetaData(
						driverVolumeWriter,
						storageType,
						dataType,
						bb.dimensionsAsLongArray(),
						compression,
						blocksize(),
						this.downsampling == null ? new int[][] {{1,1,1}} : this.downsampling,
						viewId,
						path,
						xmlOut,
						instantiate );

				if ( mrInfo == null )
					return false;
			}
			catch (SpimDataException | IOException e)
			{
				e.printStackTrace();
				IOFunctions.println( "Failed to write metadata for '"  + "': " + e );
				return false;
			}
		}
		else
		{
			// this is the relative path to the dataset inside the Zarr/N5/HDF5 container, thus using File here seems fine
			final String dataset = new File( new File( baseDataset , title ).toString(), datasetExtension ).toString();

			// e.g. in Windows this will change it to '\s0'
			final String datasetExtensionOS = new File( datasetExtension ).toString(); 

			// setup multi-resolution pyramid
			mrInfo = N5ApiTools.setupMultiResolutionPyramid(
					driverVolumeWriter,
					(level) -> new File( dataset.substring(0, dataset.lastIndexOf( datasetExtensionOS ) ) + "/s" + level ).toString(),
					dataType,
					bb.dimensionsAsLongArray(),
					compression,
					blocksize(),
					this.downsampling );
		}

		final List<long[][]> grid = N5ApiTools.assembleJobs(
				mrInfo[ 0 ],
				new int[] {
						blocksize()[0] * computeBlocksizeFactor()[ 0 ],
						blocksize()[1] * computeBlocksizeFactor()[ 1 ],
						blocksize()[2] * computeBlocksizeFactor()[ 2 ]
				} );

		IOFunctions.println( "num blocks = " + Grid.create( bb.dimensionsAsLongArray(), blocksize() ).size() + ", size = " + bsX + "x" + bsY + "x" + bsZ );
		IOFunctions.println( "num compute blocks = " + grid.size() + ", size = " + bsX*bsFactorX + "x" + bsY*bsFactorY + "x" + bsZ*bsFactorZ );

		final AtomicInteger progress = new AtomicInteger( 0 );
		IJ.showProgress( progress.get(), grid.size() );

		//
		// save full-resolution data (s0)
		//

		// TODO: use Tobi's code (at least for the special cases)
		final ForkJoinPool myPool = new ForkJoinPool(  Threads.numThreads() );

		long time = System.currentTimeMillis();

		try
		{
			myPool.submit(() ->
				grid.parallelStream().forEach(
						gridBlock -> {
							try {
	
								final Interval block =
										Intervals.translate(
												new FinalInterval( gridBlock[1] ), // blocksize
												gridBlock[0] ); // block offset
		
								final RandomAccessibleInterval< T > source = Views.interval( img, block );
		
								final RandomAccessibleInterval sourceGridBlock = Views.offsetInterval(source, gridBlock[0], gridBlock[1]);
								N5Utils.saveBlock(sourceGridBlock, driverVolumeWriter, mrInfo[ 0 ].dataset, gridBlock[2]);

								IJ.showProgress( progress.incrementAndGet(), grid.size() );
							}
							catch (Exception e) 
							{
								IOFunctions.println( "Error writing block offset=" + Util.printCoordinates( gridBlock[0] ) + "' ... " );
								e.printStackTrace();
							}
						} )
				).get();

			//myPool.shutdown();
		}
		catch (InterruptedException | ExecutionException e)
		{
			IOFunctions.println( "Failed to write HDF5/N5/ZARR dataset '" + mrInfo[ 0 ].dataset + "'. Error: " + e );
			e.printStackTrace();
			return false;
		}

		//System.out.println( "Saved, e.g. view with './n5-view -i " + n5Path + " -d " + n5Dataset );
		IJ.showProgress( progress.getAndSet( 0 ), grid.size() );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saved full resolution, took: " + (System.currentTimeMillis() - time ) + " ms." );

		//
		// save multiresolution pyramid (s1 ... sN)
		//
		for ( int level = 1; level < mrInfo.length; ++level )
		{
			final int s = level;
			final List<long[][]> allBlocks = N5ApiTools.assembleJobs( mrInfo[ level ] );

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Downsampling: " + Util.printCoordinates( mrInfo[ level ].absoluteDownsampling ) + " with relative downsampling of " + Util.printCoordinates( mrInfo[ level ].relativeDownsampling ));
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": s" + level + " num blocks=" + allBlocks.size() );
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading '" + mrInfo[ level - 1 ].dataset + "', downsampled will be written as '" + mrInfo[ level ].dataset + "'." );
			IJ.showProgress( progress.get(), allBlocks.size() );

			time = System.currentTimeMillis();

			try
			{
				myPool.submit( () -> allBlocks.parallelStream().forEach(
						gridBlock ->
						{
							N5ApiTools.writeDownsampledBlock(
								driverVolumeWriter,
								mrInfo[ s ],
								mrInfo[ s - 1 ],
								gridBlock );

							IJ.showProgress( progress.incrementAndGet(), allBlocks.size() );
						})).get();

			}
			catch (InterruptedException | ExecutionException e)
			{
				IOFunctions.println( "Failed to write HDF5/N5/ZARR dataset '" + mrInfo[ level ].dataset + "'. Error: " + e );
				e.printStackTrace();
				return false;
			}

			IJ.showProgress( progress.getAndSet( 0 ), allBlocks.size() );
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saved level s " + level + ", took: " + (System.currentTimeMillis() - time ) + " ms." );
		}

		myPool.shutdown();
		try { myPool.awaitTermination( Long.MAX_VALUE, TimeUnit.HOURS); } catch (InterruptedException e) { e.printStackTrace(); }

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
				Arrays.asList( StorageFormat.values() ).stream().map( s -> s.name() ).toArray(String[]::new);

		gdInit.addChoice( "Export as ...", options, options[ defaultOption ] );

		gdInit.addMessage(
				"For local export HDF5 is a reasonable format choice (unless you need a specific one)\n"
				+ "since it supports small blocksizes, can be written multi-threaded, and produces a single file.\n\n"
				+ "For cluster/cloud - distributed fusion please check out BigStitcher-Spark.", GUIHelper.smallStatusFont, GUIHelper.neutral );

		gdInit.addMessage(
				"Note: you can always add new datasets to an existing HDF5/N5/ZARR container, so you can specify\n"
				+ "existing N5/ZARR-directories or HDF5-files. If a dataset inside a container already exists\n"
				+ "export will stop and NOT overwrite existing datasets.", GUIHelper.smallStatusFont, GUIHelper.neutral );

		PluginHelper.addCompression( gdInit, false );

		gdInit.addCheckbox( "Create a BDV/BigStitcher compatible export (HDF5/N5 are supported)", defaultBDV );

		gdInit.addMessage(
				"HDF5/BDV currently only supports 8-bit, 16-bit\n"
				+ "N5/BDV supports 8-bit, 16-bit & 32-bit",
				GUIHelper.smallStatusFont, GUIHelper.warning );

		gdInit.addMessage(
				"Note: if you selected a single image to fuse (e.g. all tiles of one channel), you can manually\n"
				+ "specify the ViewId it will be assigned in the following dialog. This is useful if you want to\n"
				+ "add the fused image to an existing dataset so you can specify a ViewId that does not exist yet.",
				GUIHelper.smallStatusFont, GUIHelper.neutral );

		gdInit.addCheckbox( "Create multi-resolution pyramid", defaultMultiRes );

		gdInit.showDialog();
		if ( gdInit.wasCanceled() )
			return false;

		final int previousExportOption = defaultOption;
		this.storageType = StorageFormat.values()[ defaultOption = gdInit.getNextChoiceIndex() ];
		this.compression = PluginHelper.parseCompression( gdInit );
		this.bdv = defaultBDV = gdInit.getNextBoolean();
		final boolean multiRes = defaultMultiRes = gdInit.getNextBoolean();
		this.splittingType = fusion.getSplittingType();
		this.instantiate = new InstantiateViewSetupBigStitcher( splittingType );

		final String name = storageType.name();
		final String ext;

		if ( storageType == StorageFormat.HDF5 )
			ext = ".h5";
		else if ( storageType == StorageFormat.N5 )
			ext = ".n5";
		else
			ext = ".zarr";

		if ( bdv && storageType == StorageFormat.ZARR )
		{
			IOFunctions.println( "BDV-compatible ZARR file not (yet) supported." );
			return false;
		}

		if ( bdv && storageType == StorageFormat.HDF5 && fusion.getPixelType() == 0 )
		{
			IOFunctions.println( "BDV-compatible HDF5 @ 32-bit not (yet) supported." );
			return false;
		}

		//
		// next dialog
		//
		final GenericDialogPlus gd = new GenericDialogPlus( "Export " + name +" using N5-API" );

		if ( defaultPathURI == null || defaultPathURI.toString().trim().length() == 0 )
			defaultPathURI = URITools.appendName( fusion.getSpimData().getBasePathURI(), defaultDatasetName + "/" + defaultDatasetName+ext );

		if ( storageType == StorageFormat.HDF5 )
			PluginHelper.addSaveAsFileField( gd, name + "_file (local only, end with "+ext+")", defaultPathURI, 80 );
		else
			PluginHelper.addSaveAsDirectoryField( gd, name + "_dataset_path (local or cloud, end with "+ext+")", defaultPathURI, 80 );

		if ( bdv )
		{
			if ( defaultXMLOutURI == null )
				defaultXMLOutURI = URITools.appendName( fusion.getSpimData().getBasePathURI(), defaultDatasetName + "/dataset.xml" );

			if ( storageType == StorageFormat.HDF5 )
				PluginHelper.addSaveAsFileField( gd, "XML_output_file (local)", defaultXMLOutURI, 80 );
			else
				PluginHelper.addSaveAsFileField( gd, "XML_output_file (local or cloud)", defaultXMLOutURI, 80 );

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
					+ "e.g. /base/fused_tp0_ch2/s0, where 's0' suggests it is full resolution. If you select multi-resolution\n"
					+ "output the dataset extension MUST end with /s0 since it will also create /s1, /s2, ...", GUIHelper.smallStatusFont, GUIHelper.neutral );
		}

		// export type changed or undefined
		/*
		if ( defaultBlocksizeX <= 0 || storageType.ordinal() != previousExportOption )
		{
			if ( storageType == StorageType.HDF5 )
			{
				defaultBlocksizeX = defaultBlocksizeX_H5;
				defaultBlocksizeY = defaultBlocksizeY_H5;
				defaultBlocksizeZ = defaultBlocksizeZ_H5;
				defaultBlocksizeFactorX = defaultBlocksizeFactorX_H5;
				defaultBlocksizeFactorY = defaultBlocksizeFactorY_H5;
				defaultBlocksizeFactorZ = defaultBlocksizeFactorZ_H5;
			}
			else
			{
				defaultBlocksizeX = defaultBlocksizeX_N5;
				defaultBlocksizeY = defaultBlocksizeY_N5;
				defaultBlocksizeZ = defaultBlocksizeZ_N5;
				defaultBlocksizeFactorX = defaultBlocksizeFactorX_N5;
				defaultBlocksizeFactorY = defaultBlocksizeFactorY_N5;
				defaultBlocksizeFactorZ = defaultBlocksizeFactorZ_N5;
			}
		}
		*/
		if ( storageType == StorageFormat.HDF5 )
		{
			gd.addMessage(
					"Default blocksize for HDF5: "+defaultBlocksizeX_H5+"x"+defaultBlocksizeY_H5+"x"+defaultBlocksizeZ_H5+"\n" +
					"Default compute blocksize for " + storageType + ": " +(defaultBlocksizeX_H5*defaultBlocksizeFactorX_H5)+"x"+(defaultBlocksizeY_H5*defaultBlocksizeFactorY_H5)+"x"+(defaultBlocksizeZ_H5*defaultBlocksizeFactorZ_H5) +
					" (factor: "+defaultBlocksizeFactorX_H5+"x"+defaultBlocksizeFactorY_H5+"x"+defaultBlocksizeFactorZ_H5+")", GUIHelper.mediumstatusNonItalicfont, GUIHelper.neutral );
		}
		else
		{
			gd.addMessage(
					"Default blocksize for N5/ZARR: "+defaultBlocksizeX_N5+"x"+defaultBlocksizeY_N5+"x"+defaultBlocksizeZ_N5+"\n" +
					"Default compute blocksize for " + storageType + ": " +(defaultBlocksizeX_N5*defaultBlocksizeFactorX_N5)+"x"+(defaultBlocksizeY_N5*defaultBlocksizeFactorY_N5)+"x"+(defaultBlocksizeZ_N5*defaultBlocksizeFactorZ_N5) +
					" (factor: "+defaultBlocksizeFactorX_N5+"x"+defaultBlocksizeFactorY_N5+"x"+defaultBlocksizeFactorZ_N5+")", GUIHelper.mediumstatusNonItalicfont, GUIHelper.neutral );
		}

		gd.addCheckbox( "Show_advanced_block_size_options (in a new dialog, current values above)", defaultAdvancedBlockSize );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		try
		{
			this.path = URITools.toURI( defaultPathURI = gd.getNextString().trim() );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Could not create URI from provided path '" + defaultPathURI+ "'. Stopping." );
			return false;
		}

		if ( !URITools.isKnownScheme( this.path ) )
		{
			IOFunctions.println( "You provided an unkown scheme ('" + this.path+ "'). Stopping." );
			return false;
		}

		if ( storageType == StorageFormat.HDF5 && !URITools.isFile( this.path ))
		{
			IOFunctions.println( "When storing as HDF5, only local paths are supported; you specified '" + this.path+ "', which appears to not be local. Stopping." );
			return false;
		}

		if ( bdv )
		{
			try
			{
				this.xmlOut = URITools.toURI( defaultXMLOutURI = gd.getNextString().trim() );//defaultXMLOut = gd.getNextString();
			}
			catch ( Exception e )
			{
				IOFunctions.println( "Could not create URI from provided path '" + defaultXMLOutURI+ "'. Stopping." );
				return false;
			}

			if ( !URITools.isKnownScheme( this.xmlOut ) )
			{
				IOFunctions.println( "You provided an unkown scheme ('" + this.xmlOut+ "'). Stopping." );
				return false;
			}

			if ( storageType == StorageFormat.HDF5 && !URITools.isFile( this.xmlOut ))
			{
				IOFunctions.println( "When storing as HDF5, only local paths are supported; you specified '" + this.xmlOut+ "', which appears to not be local. Stopping." );
				return false;
			}

			if ( fusion.getFusionGroups().size() == 1 )
			{
				this.manuallyAssignViewId = gd.getNextBoolean();
				this.tpId = (int)Math.round( gd.getNextNumber() );
				this.vsId = (int)Math.round( gd.getNextNumber() );
			}
			else
			{
				// depends on fusion group, defined during export
				// later calling getViewIdForGroup( fusionGroup, splittingType );
			}
		}
		else
		{
			this.baseDataset = defaultBaseDataset  = gd.getNextString().trim();
			this.datasetExtension = defaultDatasetExtension = gd.getNextString().trim();
		}

		if ( defaultAdvancedBlockSize = gd.getNextBoolean() )
		{
			final GenericDialog gd2 = new GenericDialog( "Compute block sizes" );

			gd2.addNumericField( "block_size_x", ( storageType == StorageFormat.HDF5 ) ? defaultBlocksizeX_H5 : defaultBlocksizeX_N5, 0);
			gd2.addNumericField( "block_size_y", ( storageType == StorageFormat.HDF5 ) ? defaultBlocksizeY_H5 : defaultBlocksizeY_N5, 0);
			gd2.addNumericField( "block_size_z", ( storageType == StorageFormat.HDF5 ) ? defaultBlocksizeZ_H5 : defaultBlocksizeZ_N5, 0);

			gd2.addNumericField( "block_size_factor_x", ( storageType == StorageFormat.HDF5 ) ? defaultBlocksizeFactorX_H5 : defaultBlocksizeFactorX_N5, 0);
			gd2.addNumericField( "block_size_factor_y", ( storageType == StorageFormat.HDF5 ) ? defaultBlocksizeFactorY_H5 : defaultBlocksizeFactorY_N5, 0);
			gd2.addNumericField( "block_size_factor_z", ( storageType == StorageFormat.HDF5 ) ? defaultBlocksizeFactorZ_H5 : defaultBlocksizeFactorZ_N5, 0);

			gd2.addMessage(
					"For smaller blocksizes (or very large images) you can define compute block sizes\n"
					+ "as a factor of the file block sizes to achieve a better parallelization.\n"
					+ "For example, if you chose a blocksize of 32x32x16 for saving, and you choose factors of 4x4x2,\n"
					+ "the compute will be performed in blocksizes of 128x128x64, which creates less tasks.", GUIHelper.smallStatusFont, GUIHelper.neutral );

			gd2.showDialog();
			if ( gd2.wasCanceled() )
				return false;

			bsX = (int)Math.round( gd2.getNextNumber() );
			bsY = (int)Math.round( gd2.getNextNumber() );
			bsZ = (int)Math.round( gd2.getNextNumber() );

			bsFactorX = (int)Math.round( gd2.getNextNumber() );
			bsFactorY = (int)Math.round( gd2.getNextNumber() );
			bsFactorZ = (int)Math.round( gd2.getNextNumber() );

			if ( storageType == StorageFormat.HDF5 )
			{
				defaultBlocksizeX_H5 = bsX; defaultBlocksizeY_H5 = bsY; defaultBlocksizeZ_H5 = bsZ;
				defaultBlocksizeFactorX_H5 = bsFactorX; defaultBlocksizeFactorY_H5 = bsFactorY; defaultBlocksizeFactorZ_H5 = bsFactorZ;
			}
			else
			{
				defaultBlocksizeX_N5 = bsX; defaultBlocksizeY_N5 = bsY; defaultBlocksizeZ_N5 = bsZ;
				defaultBlocksizeFactorX_N5 = bsFactorX; defaultBlocksizeFactorY_N5 = bsFactorY; defaultBlocksizeFactorZ_N5 = bsFactorZ;
			}
		}
		else
		{
			if ( storageType == StorageFormat.HDF5 )
			{
				bsX = defaultBlocksizeX_H5; bsY = defaultBlocksizeY_H5; bsZ = defaultBlocksizeZ_H5;
				bsFactorX = defaultBlocksizeFactorX_H5; bsFactorY = defaultBlocksizeFactorY_H5; bsFactorZ = defaultBlocksizeFactorZ_H5;
			}
			else
			{
				bsX = defaultBlocksizeX_N5; bsY = defaultBlocksizeY_N5; bsZ = defaultBlocksizeZ_N5;
				bsFactorX = defaultBlocksizeFactorX_N5; bsFactorY = defaultBlocksizeFactorY_N5; bsFactorZ = defaultBlocksizeFactorZ_N5;
			}
		}

		if ( multiRes )
		{
			if ( !bdv && !this.datasetExtension.endsWith("/s0") )
			{
				IOFunctions.println( "The selected dataset extension does not end with '/s0'. Cannot continue since it is unclear how to store multi-resolution levels '/s1', '/s2', ..." );
				return false;
			}

			final double aniso = fusion.getAnisotropyFactor();
			final Interval bb = fusion.getDownsampledBoundingBox();
			final int[][] proposedDownsampling = estimateMultiResPyramid( new FinalDimensions( bb.dimensionsAsLongArray() ), aniso );

			final GenericDialog gdp = new GenericDialog( "Adjust downsampling options" );

			gdp.addStringField( "Subsampling_factors (downsampling)", ProposeMipmaps.getArrayString( proposedDownsampling ), 40 );
			gdp.addMessage( "Blocksize: "+bsX+"x"+bsY+"x"+bsZ, GUIHelper.mediumstatusNonItalicfont, GUIHelper.neutral );

			gdp.showDialog();
			if ( gdp.wasCanceled() )
				return false;

			final String subsampling = gdp.getNextString();
			this.downsampling = PluginHelper.parseResolutionsString( subsampling );

			if ( this.downsampling == null || downsampling.length == 0 || downsampling[0] == null || downsampling[0].length == 0)
			{
				IOFunctions.println( "Could not parse resolution level string '" + subsampling + "'. Stopping." );
				return false;
			}

			String mres = "[" + Util.printCoordinates( downsampling[ 0 ] );
			for ( int i = 1; i < downsampling.length; ++i )
				mres += ", " + Util.printCoordinates( downsampling[ i ] );
			mres += "]";
			IOFunctions.println( "Multi-resolution steps: " + mres);
		}
		else
		{
			this.downsampling = new int[][] {{1,1,1}}; // no downsampling
		}

		return true;
	}

	public static int[][] estimateMultiResPyramid( final Dimensions dimensions, final double aniso )
	{
		final VoxelDimensions v = new FinalVoxelDimensions( "px", 1.0, 1.0, Double.isNaN( aniso ) ? 1.0 : aniso );
		final BasicViewSetup setup = new BasicViewSetup(0, "fusion", dimensions, v );
		final ExportMipmapInfo emi = ProposeMipmaps.proposeMipmaps( setup );

		return emi.getExportResolutions();
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
