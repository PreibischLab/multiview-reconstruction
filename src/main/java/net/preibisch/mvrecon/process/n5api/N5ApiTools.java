package net.preibisch.mvrecon.process.n5api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.export.ExportMipmapInfo;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.downsampling.lazy.LazyHalfPixelDownsample2x;
import net.preibisch.mvrecon.process.export.ExportN5API.StorageType;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import util.Grid;

public class N5ApiTools
{
	public static ViewId gridBlockToViewId( final long[][] gridBlock )
	{
		if ( gridBlock.length <= 3 )
			throw new RuntimeException( "gridBlockToViewId() needs an extended GridBlock long[][], where Gridblock[3][] encodes the ViewId");

		return new ViewId( (int)gridBlock[ 3 ][ 0 ], (int)gridBlock[ 3 ][ 1 ]);
	}

	/**
	 * @param level - the downsampling level
	 * @param storageType - N5 or HDF5 (soon Zarr)
	 * @return a Function that maps the gridBlock to a N5 dataset name
	 */
	public static Function<long[][], String> gridToDatasetBdv( final int level, final StorageType storageType )
	{
		return (gridBlock) -> viewIdToDatasetBdv( level, storageType ).apply( gridBlockToViewId( gridBlock ) );
	}

	/**
	 * @param level - the downsampling level
	 * @param storageType - N5 or HDF5 (soon Zarr)
	 * @return a Function that maps the ViewId to a N5 dataset name
	 */
	public static Function<ViewId, String> viewIdToDatasetBdv( final int level, final StorageType storageType )
	{
		return (viewId) -> createBDVPath( viewId, level, storageType );
	}

	/**
	 * @param storageType - N5 or HDF5 (soon Zarr)
	 * @return a Function that maps (ViewId, level) to a N5 dataset name
	 */
	public static BiFunction<ViewId, Integer, String> viewIdToDatasetBdv( final StorageType storageType )
	{
		return (viewId, level) -> viewIdToDatasetBdv( level, storageType ).apply( viewId );
	}

	/**
	 * @param storageType - N5 or HDF5 (soon Zarr)
	 * @return a Function that maps (gridBlock, level) to a N5 dataset name
	 */
	public static BiFunction<long[][], Integer, String> gridToDatasetBdv( final StorageType storageType )
	{
		return (gridBlock, level) -> gridToDatasetBdv( level, storageType ).apply( gridBlock );
	}

	public static ViewId getViewId(final String bdvString )
	{
		final String[] entries = bdvString.trim().split( "," );
		final int timepointId = Integer.parseInt( entries[ 0 ].trim() );
		final int viewSetupId = Integer.parseInt( entries[ 1 ].trim() );

		return new ViewId(timepointId, viewSetupId);
	}

	public static String createBDVPath(final String bdvString, final int level, final StorageType storageType)
	{
		return createBDVPath( getViewId( bdvString ), level, storageType);
	}

	public static String createBDVPath( final ViewId viewId, final int level, final StorageType storageType)
	{
		String path = null;

		if ( StorageType.N5.equals(storageType) )
		{
			path = "setup" + viewId.getViewSetupId() + "/" + "timepoint" + viewId.getTimePointId() + "/s" + level;
		}
		else if ( StorageType.HDF5.equals(storageType) )
		{
			path = "t" + String.format("%05d", viewId.getTimePointId()) + "/" + "s" + String.format("%02d", viewId.getViewSetupId()) + "/" + level + "/cells";
		}
		else
		{
			new RuntimeException( "BDV-compatible dataset cannot be written for " + storageType + " (yet).");
		}

		return path;
	}

	public static String createDownsampledBDVPath( final String s0path, final int level, final StorageType storageType )
	{
		if ( StorageType.N5.equals(storageType) )
		{
			return s0path.substring( 0, s0path.length() - 3 ) + "/s" + level;
		}
		else if ( StorageType.HDF5.equals(storageType) )
		{
			return s0path.substring( 0, s0path.length() - 8 ) + "/" + level + "/cells";
		}
		else
		{
			throw new RuntimeException( "BDV-compatible dataset cannot be written for " + storageType + " (yet).");
		}
	}

	public static class MultiResolutionLevelInfo implements Serializable
	{
		private static final long serialVersionUID = 5392269335394869108L;

		final public int[] relativeDownsampling, absoluteDownsampling, blockSize;
		final public long[] dimensions;
		final public String dataset;
		final public DataType dataType;

		public MultiResolutionLevelInfo(
				final String dataset,
				final long[] dimensions,
				final DataType dataType,
				final int[] relativeDownsampling,
				final int[] absoluteDownsampling,
				final int[] blockSize )
		{
			this.dataset = dataset;
			this.dimensions = dimensions;
			this.dataType = dataType;
			this.relativeDownsampling = relativeDownsampling;
			this.absoluteDownsampling = absoluteDownsampling;
			this.blockSize = blockSize;
		}
	}

	public static MultiResolutionLevelInfo[] setupMultiResolutionPyramid(
			final N5Writer driverVolumeWriter,
			final Function<Integer, String> levelToDataset,
			final DataType dataType,
			final long[] dimensionsS0,
			final Compression compression,
			final int[] blockSize,
			final int[][] downsamplings )
	{
		return setupMultiResolutionPyramid(
				driverVolumeWriter,
				null,
				(viewId, level) -> levelToDataset.apply( level ),
				dataType,
				dimensionsS0,
				compression,
				blockSize,
				downsamplings);
	}

	public static MultiResolutionLevelInfo[] setupMultiResolutionPyramid(
			final N5Writer driverVolumeWriter,
			final ViewId viewId,
			final BiFunction<ViewId, Integer, String> viewIdToDataset,
			final DataType dataType,
			final long[] dimensionsS0,
			final Compression compression,
			final int[] blockSize,
			final int[][] downsamplings )
	{
		final MultiResolutionLevelInfo[] mrInfo = new MultiResolutionLevelInfo[ downsamplings.length];

		// set up s0
		int[] relativeDownsampling = downsamplings[ 0 ].clone();
		Arrays.setAll( relativeDownsampling, i -> 1 );

		mrInfo[ 0 ] = new MultiResolutionLevelInfo(
				viewIdToDataset.apply( viewId, 0 ), dimensionsS0.clone(), dataType, relativeDownsampling, downsamplings[ 0 ], blockSize );

		driverVolumeWriter.createDataset(
				viewIdToDataset.apply( viewId, 0 ),
				dimensionsS0,
				blockSize,
				dataType,
				compression );

		long[] previousDim = dimensionsS0.clone();

		// set up s1 ... sN
		for ( int level = 1; level < downsamplings.length; ++level )
		{
			relativeDownsampling = computeRelativeDownsampling( downsamplings, level );

			final String datasetLevel = viewIdToDataset.apply( viewId, level );

			final long[] dim = new long[ previousDim.length ];
			for ( int d = 0; d < dim.length; ++d )
				dim[ d ] = previousDim[ d ] / relativeDownsampling[ d ];

			mrInfo[ level ] = new MultiResolutionLevelInfo(
					datasetLevel, dim.clone(), dataType, relativeDownsampling, downsamplings[ level ], blockSize );

			driverVolumeWriter.createDataset(
					datasetLevel,
					dim,
					blockSize,
					dataType,
					compression );

			previousDim = dim;
		}

		return mrInfo;
	}

	public static MultiResolutionLevelInfo[] setupBdvDatasetsHDF5(
			final N5Writer driverVolumeWriter,
			final ViewId viewId,
			final DataType dataType,
			final long[] dimensions,
			final Compression compression,
			final int[] blockSize,
			final int[][] downsamplings )
	{
		final MultiResolutionLevelInfo[] mrInfo = setupMultiResolutionPyramid(
				driverVolumeWriter,
				viewId,
				viewIdToDatasetBdv( StorageType.N5 ),
				dataType,
				dimensions,
				compression,
				blockSize,
				downsamplings);

		final String subdivisionsDatasets = "s" + String.format("%02d", viewId.getViewSetupId()) + "/subdivisions";
		final String resolutionsDatasets = "s" + String.format("%02d", viewId.getViewSetupId()) + "/resolutions";

		if ( !driverVolumeWriter.datasetExists( subdivisionsDatasets ) || !driverVolumeWriter.datasetExists( resolutionsDatasets ) )
		{
			final Img<IntType> subdivisions;
			final Img<DoubleType> resolutions;
	
			if ( downsamplings == null || downsamplings.length == 0 )
			{
				subdivisions = ArrayImgs.ints( blockSize, new long[] { 3, 1 } ); // blocksize
				resolutions = ArrayImgs.doubles( new double[] { 1,1,1 }, new long[] { 3, 1 } ); // downsampling
			}
			else
			{
				final int[] blocksizes = new int[ 3 * downsamplings.length ];
				final double[] downsamples = new double[ 3 * downsamplings.length ];
	
				int i = 0;
				for ( int level = 0; level < downsamplings.length; ++level )
				{
					downsamples[ i ] = downsamplings[ level ][ 0 ];
					blocksizes[ i++ ] = blockSize[ 0 ];
					downsamples[ i ] = downsamplings[ level ][ 1 ];
					blocksizes[ i++ ] = blockSize[ 1 ];
					downsamples[ i ] = downsamplings[ level ][ 2 ];
					blocksizes[ i++ ] = blockSize[ 2 ];
				}
	
				subdivisions = ArrayImgs.ints( blocksizes, new long[] { 3, downsamplings.length } ); // blocksize
				resolutions = ArrayImgs.doubles( downsamples, new long[] { 3, downsamplings.length } ); // downsampling
			}
			
			driverVolumeWriter.createDataset(
					subdivisionsDatasets,
					subdivisions.dimensionsAsLongArray(),// new long[] { 3, 1 },
					new int[] { (int)subdivisions.dimension( 0 ), (int)subdivisions.dimension( 1 ) }, //new int[] { 3, 1 },
					DataType.INT32,
					new RawCompression() );
	
			driverVolumeWriter.createDataset(
					resolutionsDatasets,
					resolutions.dimensionsAsLongArray(),// new long[] { 3, 1 },
					new int[] { (int)resolutions.dimension( 0 ), (int)resolutions.dimension( 1 ) },//new int[] { 3, 1 },
					DataType.FLOAT64,
					new RawCompression() );
	
			N5Utils.saveBlock(subdivisions, driverVolumeWriter, "s" + String.format("%02d", viewId.getViewSetupId()) + "/subdivisions", new long[] {0,0,0} );
			N5Utils.saveBlock(resolutions, driverVolumeWriter, "s" + String.format("%02d", viewId.getViewSetupId()) + "/resolutions", new long[] {0,0,0} );
		}

		return mrInfo;
	}

	public static MultiResolutionLevelInfo[] setupBdvDatasetsN5(
			final N5Writer driverVolumeWriter,
			final ViewId viewId,
			final DataType dataType,
			final long[] dimensions,
			final Compression compression,
			final int[] blockSize,
			int[][] downsamplings )
	{
		final String s0Dataset = createBDVPath( viewId, 0, StorageType.N5 );
		final String setupDataset = s0Dataset.substring(0, s0Dataset.indexOf( "/timepoint" ));
		final String timepointDataset = s0Dataset.substring(0, s0Dataset.indexOf("/s0" ));

		final MultiResolutionLevelInfo[] mrInfo = setupMultiResolutionPyramid(
				driverVolumeWriter,
				viewId,
				viewIdToDatasetBdv( StorageType.N5 ),
				dataType,
				dimensions,
				compression,
				blockSize,
				downsamplings);

		final Map<String, Class<?>> attribs = driverVolumeWriter.listAttributes( setupDataset );

		// if viewsetup does not exist
		if ( !attribs.containsKey( DatasetAttributes.DATA_TYPE_KEY ) || !attribs.containsKey( DatasetAttributes.BLOCK_SIZE_KEY ) || !attribs.containsKey( DatasetAttributes.DIMENSIONS_KEY ) || !attribs.containsKey( DatasetAttributes.COMPRESSION_KEY ) || !attribs.containsKey( "downsamplingFactors" ) )
		{
			// set N5 attributes for setup
			// e.g. {"compression":{"type":"gzip","useZlib":false,"level":1},"downsamplingFactors":[[1,1,1],[2,2,1]],"blockSize":[128,128,32],"dataType":"uint16","dimensions":[512,512,86]}
			IOFunctions.println( "setting attributes for '" + "setup" + viewId.getViewSetupId() + "'");

			final HashMap<String, Object > attribs2 = new HashMap<>();
			attribs2.put(DatasetAttributes.DATA_TYPE_KEY, dataType );
			attribs2.put(DatasetAttributes.BLOCK_SIZE_KEY, blockSize );
			attribs2.put(DatasetAttributes.DIMENSIONS_KEY, dimensions );
			attribs2.put(DatasetAttributes.COMPRESSION_KEY, compression );

			if ( downsamplings == null || downsamplings.length == 0 )
				attribs2.put( "downsamplingFactors", new int[][] {{1,1,1}} );
			else
				attribs2.put( "downsamplingFactors", downsamplings );

			driverVolumeWriter.setAttributes (setupDataset, attribs2 );
		}
		else
		{
			// TODO: test that the values are consistent?
		}

		// set N5 attributes for timepoint
		// e.g. {"resolution":[1.0,1.0,3.0],"saved_completely":true,"multiScale":true}
		driverVolumeWriter.setAttribute(timepointDataset, "resolution", new double[] {1,1,1} );
		driverVolumeWriter.setAttribute(timepointDataset, "saved_completely", true );
		driverVolumeWriter.setAttribute(timepointDataset, "multiScale", downsamplings != null && downsamplings.length != 0 );

		if ( downsamplings == null || downsamplings.length == 0 )
		{
			downsamplings = new int[1][ dimensions.length ];
			Arrays.setAll( downsamplings[ 0 ], i -> 1 );
		}

		// set additional N5 attributes for s0 ... sN datasets
		for ( int level = 0; level < downsamplings.length; ++level )
			driverVolumeWriter.setAttribute( mrInfo[ level ].dataset, "downsamplingFactors", mrInfo[ level ].absoluteDownsampling );

		return mrInfo;
	}

	public static void writeDownsampledBlock(
			final N5Writer n5,
			final MultiResolutionLevelInfo mrInfo,
			final MultiResolutionLevelInfo mrInfoPreviousScale,
			//final Function<long[][], String> viewIdToDataset, // gridBlock to dataset name (e.g. for s1, s2, ...)
			//final Function<long[][], String> viewIdToDatasetPreviousScale, // gridblock to name of previous dataset (e.g. for s0 when writing s1, s1 when writing s2, ... )
			//final int[] relativeDownsampling,
			final long[][] gridBlock )
	{
		final String dataset = mrInfo.dataset;// viewIdToDataset.apply( gridBlock );
		final String datasetPreviousScale = mrInfoPreviousScale.dataset; // viewIdToDatasetPreviousScale.apply( gridBlock );

		final DataType dataType = mrInfo.dataType;// n5.getAttribute( datasetPreviousScale, DatasetAttributes.DATA_TYPE_KEY, DataType.class );
		final int[] blockSize = mrInfo.blockSize;// n5.getAttribute( datasetPreviousScale, DatasetAttributes.BLOCK_SIZE_KEY, int[].class );

		if ( dataType == DataType.UINT16 )
		{
			RandomAccessibleInterval<UnsignedShortType> downsampled = N5Utils.open(n5, datasetPreviousScale);

			for ( int d = 0; d < downsampled.numDimensions(); ++d )
				if ( mrInfo.relativeDownsampling[ d ] > 1 )
					downsampled = LazyHalfPixelDownsample2x.init(
						downsampled,
						new FinalInterval( downsampled ),
						new UnsignedShortType(),
						blockSize,
						d);

			final RandomAccessibleInterval<UnsignedShortType> sourceGridBlock = Views.offsetInterval(downsampled, gridBlock[0], gridBlock[1]);
			N5Utils.saveNonEmptyBlock(sourceGridBlock, n5, dataset, gridBlock[2], new UnsignedShortType());
		}
		else if ( dataType == DataType.UINT8 )
		{
			RandomAccessibleInterval<UnsignedByteType> downsampled = N5Utils.open(n5, datasetPreviousScale);

			for ( int d = 0; d < downsampled.numDimensions(); ++d )
				if ( mrInfo.relativeDownsampling[ d ] > 1 )
					downsampled = LazyHalfPixelDownsample2x.init(
						downsampled,
						new FinalInterval( downsampled ),
						new UnsignedByteType(),
						blockSize,
						d);

			final RandomAccessibleInterval<UnsignedByteType> sourceGridBlock = Views.offsetInterval(downsampled, gridBlock[0], gridBlock[1]);
			N5Utils.saveNonEmptyBlock(sourceGridBlock, n5, dataset, gridBlock[2], new UnsignedByteType());
		}
		else if ( dataType == DataType.FLOAT32 )
		{
			RandomAccessibleInterval<FloatType> downsampled = N5Utils.open(n5, datasetPreviousScale);;

			for ( int d = 0; d < downsampled.numDimensions(); ++d )
				if ( mrInfo.relativeDownsampling[ d ] > 1 )
					downsampled = LazyHalfPixelDownsample2x.init(
						downsampled,
						new FinalInterval( downsampled ),
						new FloatType(),
						blockSize,
						d);

			final RandomAccessibleInterval<FloatType> sourceGridBlock = Views.offsetInterval(downsampled, gridBlock[0], gridBlock[1]);
			N5Utils.saveNonEmptyBlock(sourceGridBlock, n5, dataset, gridBlock[2], new FloatType());
		}
		else
		{
			n5.close();
			throw new RuntimeException("Unsupported pixel type: " + dataType );
		}
	}

	public static ArrayList<long[][]> assembleJobs( final MultiResolutionLevelInfo mrInfo )
	{
		return assembleJobs( null, mrInfo );
	}

	public static ArrayList<long[][]> assembleJobs( final MultiResolutionLevelInfo mrInfo, final int[] computeBlockSize )
	{
		return assembleJobs( null, mrInfo, computeBlockSize );
	}

	public static ArrayList<long[][]> assembleJobs(
			final ViewId viewId,
			final MultiResolutionLevelInfo mrInfo )
	{
		return assembleJobs( viewId, mrInfo.dimensions, mrInfo.blockSize, mrInfo.blockSize );
	}

	public static ArrayList<long[][]> assembleJobs(
			final ViewId viewId,
			final MultiResolutionLevelInfo mrInfo,
			final int[] computeBlockSize )
	{
		return assembleJobs( viewId, mrInfo.dimensions, mrInfo.blockSize, computeBlockSize );
	}

	public static ArrayList<long[][]> assembleJobs(
			final long[] dimensions,
			final int[] blockSize )
	{
		return assembleJobs(null, dimensions, blockSize, blockSize );
	}

	public static ArrayList<long[][]> assembleJobs(
			final long[] dimensions,
			final int[] blockSize,
			final int[] computeBlockSize )
	{
		return assembleJobs(null, dimensions, blockSize, computeBlockSize );
	}

	public static ArrayList<long[][]> assembleJobs(
			final ViewId viewId, //can be null 
			final long[] dimensions,
			final int[] blockSize )
	{
		return assembleJobs(viewId, dimensions, blockSize, blockSize );
	}

	public static ArrayList<long[][]> assembleJobs(
			final ViewId viewId, //can be null 
			final long[] dimensions,
			final int[] blockSize,
			final int[] computeBlockSize )
	{
		// all blocks (a.k.a. grids)
		final ArrayList<long[][]> allBlocks = new ArrayList<>();

		final List<long[][]> grid = Grid.create(
				dimensions,
				computeBlockSize,
				blockSize);

		if ( viewId != null )
		{
			// add timepointId and ViewSetupId & dimensions to the gridblock
			for ( final long[][] gridBlock : grid )
				allBlocks.add( new long[][]{
					gridBlock[ 0 ].clone(),
					gridBlock[ 1 ].clone(),
					gridBlock[ 2 ].clone(),
					new long[] { viewId.getTimePointId(), viewId.getViewSetupId() }
				});
		}

		return allBlocks;
	}

	public static int[] computeRelativeDownsampling(
			final int[][] downsamplings,
			final int level )
	{
		final int[] ds = new int[ downsamplings[ 0 ].length ];

		for ( int d = 0; d < ds.length; ++d )
			ds[ d ] = downsamplings[ level ][ d ] / downsamplings[ level - 1 ][ d ];

		return ds;
	}

	public static <T extends NativeType<T>> void resaveS0Block(
			final SpimData2 data,
			final N5Writer n5,
			final DataType dataType,
			final Function<long[][], String> gridBlockToDataset, // gridBlock to dataset name for s0
			final long[][] gridBlock )
	{
		final ViewId viewId = gridBlockToViewId( gridBlock );
		final String dataset = gridBlockToDataset.apply( gridBlock );

		if ( dataType != DataType.UINT16 && dataType != DataType.UINT8 && dataType != DataType.FLOAT32 )
		{
			n5.close();
			throw new RuntimeException("Unsupported pixel type: " + dataType );
		}

		final SetupImgLoader< ? > imgLoader = data.getSequenceDescription().getImgLoader().getSetupImgLoader( viewId.getViewSetupId() );
		final RandomAccessibleInterval< T > img = Cast.unchecked( imgLoader.getImage( viewId.getTimePointId() ) );
		final RandomAccessibleInterval< T > sourceGridBlock = Views.offsetInterval( img, gridBlock[ 0 ], gridBlock[ 1 ] );
		N5Utils.saveNonEmptyBlock( sourceGridBlock, n5, dataset, gridBlock[ 2 ], img.getType().createVariable() );

		System.out.println( "ViewId " + Group.pvid( viewId ) + ", written block: offset=" + Util.printCoordinates( gridBlock[0] ) + ", dimension=" + Util.printCoordinates( gridBlock[1] ) );
	}

	public static Map< Integer, DataType > assembleDataTypes(
			final AbstractSpimData<?> data,
			final Collection< Integer > viewSetupIds )
	{
		final HashMap< Integer, DataType > dataTypes = new HashMap<>();

		for ( final int viewSetupId : viewSetupIds )
		{
			final Object type = data.getSequenceDescription().getImgLoader().getSetupImgLoader( viewSetupId ).getImageType();
			final DataType dataType;
	
			if ( UnsignedShortType.class.isInstance( type ) )
				dataType = DataType.UINT16;
			else if ( UnsignedByteType.class.isInstance( type ) )
				dataType = DataType.UINT8;
			else if ( FloatType.class.isInstance( type ) )
				dataType = DataType.FLOAT32;
			else
				throw new RuntimeException("Unsupported pixel type: " + type.getClass().getCanonicalName() );
	
			dataTypes.put( viewSetupId, dataType );
		}

		return dataTypes;
	}

	public static int[][] mipMapInfoToDownsamplings( final Map< Integer, ExportMipmapInfo > mipmaps )
	{
		int[][] downsamplings = mipmaps.values().iterator().next().getExportResolutions();

		// just find the biggest one (most steps) and use it for all
		for ( final ExportMipmapInfo info : mipmaps.values() )
			if (info.getExportResolutions().length > downsamplings.length)
				downsamplings = info.getExportResolutions();

		return downsamplings;
	}

	public static HashMap<Integer, long[]> assembleDimensions(
			final SpimData data,
			final Collection< ? extends ViewId > viewIds )
	{
		final HashMap< Integer, long[] > viewSetupIdToDimensions = new HashMap<>();
		final Map<ViewId, ViewDescription> map = data.getSequenceDescription().getViewDescriptions();

		viewIds.forEach( viewId -> viewSetupIdToDimensions.put( viewId.getViewSetupId(), map.get( viewId ).getViewSetup().getSize().dimensionsAsLongArray() ) );

		return viewSetupIdToDimensions;
	}

	public static ArrayList< ViewSetup > assembleViewSetups(
			final SpimData data,
			final Collection< ? extends ViewId > viewIds )
	{
		final ArrayList< ViewSetup > list = new ArrayList<>();
		final Map<ViewId, ViewDescription> map = data.getSequenceDescription().getViewDescriptions();

		viewIds.forEach( viewId -> list.add( map.get( viewId ).getViewSetup() ) );

		return list;
	}
}
