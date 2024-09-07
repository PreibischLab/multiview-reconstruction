package net.preibisch.mvrecon.process.resave;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Writer;
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
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.downsampling.lazy.LazyHalfPixelDownsample2x;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import util.Grid;

public class N5ResaveTools
{
	/**
	 * @param level - the downsampling level
	 * @return a Function that maps the gridBlock to a N5 dataset name
	 */
	public static Function<long[][], String> mappingFunctionBDV( final int level )
	{
		return gridBlock ->
		{
			final ViewId viewId = gridBlock.length > 3 ? new ViewId( (int)gridBlock[ 3 ][ 0 ], (int)gridBlock[ 3 ][ 1 ]) : new ViewId( 0, 0 );
			return "setup" + viewId.getViewSetupId() + "/timepoint" + viewId.getTimePointId() + "/s" + (level);
		};
	}

	public static void writeDownsampledBlock(
			final N5Writer n5,
			final Function<long[][], String> viewIdToDataset, // gridBlock to dataset name (e.g. s1, s2, ...)
			final Function<long[][], String> viewIdToDatasetPreviousScale, // gridblock to name of previous dataset (e.g. s0 when writing s1, s1 when writing s2, ... )
			final int[] relativeDownsampling,
			final long[][] gridBlock )
	{
		final String dataset = viewIdToDataset.apply( gridBlock );
		final String datasetPreviousScale = viewIdToDatasetPreviousScale.apply( gridBlock );

		final DataType dataType = n5.getAttribute( datasetPreviousScale, DatasetAttributes.DATA_TYPE_KEY, DataType.class );
		final int[] blockSize = n5.getAttribute( datasetPreviousScale, DatasetAttributes.BLOCK_SIZE_KEY, int[].class );
		//final String datasetPrev = "setup" + viewId.getViewSetupId() + "/timepoint" + viewId.getTimePointId() + "/s" + (level-1);
		//final String dataset = "setup" + viewId.getViewSetupId() + "/timepoint" + viewId.getTimePointId() + "/s" + (level);

		if ( dataType == DataType.UINT16 )
		{
			RandomAccessibleInterval<UnsignedShortType> downsampled = N5Utils.open(n5, datasetPreviousScale);

			for ( int d = 0; d < downsampled.numDimensions(); ++d )
				if ( relativeDownsampling[ d ] > 1 )
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
				if ( relativeDownsampling[ d ] > 1 )
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
				if ( relativeDownsampling[ d ] > 1 )
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

	public static ArrayList<long[][]> prepareDownsampling(
			final Collection< ? extends ViewId > viewIds,
			final N5Writer n5,
			final int level,
			final int[] relativeDownsampling,
			final int[] absoluteDownsampling,
			final int[] blockSize,
			final Compression compression )
	{
		// all blocks (a.k.a. grids) across all ViewId's
		final ArrayList<long[][]> allBlocks = new ArrayList<>();

		// adjust dimensions
		for ( final ViewId viewId : viewIds )
		{
			final long[] previousDim = n5.getAttribute( "setup" + viewId.getViewSetupId() + "/timepoint" + viewId.getTimePointId() + "/s" + (level-1), "dimensions", long[].class );
			final long[] dim = new long[ previousDim.length ];
			for ( int d = 0; d < dim.length; ++d )
				dim[ d ] = previousDim[ d ] / relativeDownsampling[ d ];
			final DataType dataType = n5.getAttribute( "setup" + viewId.getViewSetupId(), "dataType", DataType.class );

			System.out.println( Group.pvid( viewId ) + ": s" + (level-1) + " dim=" + Util.printCoordinates( previousDim ) + ", s" + level + " dim=" + Util.printCoordinates( dim ) + ", datatype=" + dataType );

			final String dataset = "setup" + viewId.getViewSetupId() + "/timepoint" + viewId.getTimePointId() + "/s" + level;

			try
			{
				n5.createDataset(
						dataset,
						dim, // dimensions
						blockSize,
						dataType,
						compression );
			}
			catch ( Exception e )
			{
				IOFunctions.println( "Couldn't create downsampling level " + level + ", dataset '" + dataset + "': " + e );
				return null;
			}

			final List<long[][]> grid = Grid.create(
					dim,
					new int[] {
							blockSize[0],
							blockSize[1],
							blockSize[2]
					},
					blockSize);

			// add timepointId and ViewSetupId to the gridblock
			for ( final long[][] gridBlock : grid )
				allBlocks.add( new long[][]{
					gridBlock[ 0 ].clone(),
					gridBlock[ 1 ].clone(),
					gridBlock[ 2 ].clone(),
					new long[] { viewId.getTimePointId(), viewId.getViewSetupId() }
				});

			// set additional N5 attributes for sN dataset
			n5.setAttribute(dataset, "downsamplingFactors", absoluteDownsampling[ level ] );
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

	public static void createS0Datasets(
			final N5Writer n5,
			final Collection< ? extends ViewId > viewIds,
			final Map<Integer, long[]> viewSetupIdToDimensions,
			final int[] blockSize,
			final Compression compression )
	{
		for ( final ViewId viewId : viewIds )
		{
			IOFunctions.println( "Creating dataset for " + Group.pvid( viewId ) );
			
			final String dataset = "setup" + viewId.getViewSetupId() + "/timepoint" + viewId.getTimePointId() + "/s0";
			final DataType dataType = n5.getAttribute( "setup" + viewId.getViewSetupId(), "dataType", DataType.class );
	
			n5.createDataset(
					dataset,
					viewSetupIdToDimensions.get( viewId.getViewSetupId() ), // dimensions
					blockSize,
					dataType,
					compression );
	
			System.out.println( "Setting attributes for " + Group.pvid( viewId ) );
	
			// set N5 attributes for timepoint
			// e.g. {"resolution":[1.0,1.0,3.0],"saved_completely":true,"multiScale":true}
			String ds ="setup" + viewId.getViewSetupId() + "/" + "timepoint" + viewId.getTimePointId();
			n5.setAttribute(ds, "resolution", new double[] {1,1,1} );
			n5.setAttribute(ds, "saved_completely", true );
			n5.setAttribute(ds, "multiScale", true );
	
			// set additional N5 attributes for s0 dataset
			ds = ds + "/s0";
			n5.setAttribute(ds, "downsamplingFactors", new int[] {1,1,1} );
		}
	}

	public static void writeS0Block(
			final SpimData2 data,
			final N5Writer n5,
			final long[][] gridBlock )
	{
		final ViewId viewId = new ViewId( (int)gridBlock[ 3 ][ 0 ], (int)gridBlock[ 3 ][ 1 ]);

		final SetupImgLoader< ? > imgLoader = data.getSequenceDescription().getImgLoader().getSetupImgLoader( viewId.getViewSetupId() );

		@SuppressWarnings("rawtypes")
		final RandomAccessibleInterval img = imgLoader.getImage( viewId.getTimePointId() );

		final DataType dataType = n5.getAttribute( "setup" + viewId.getViewSetupId(), "dataType", DataType.class );
		final String dataset = "setup" + viewId.getViewSetupId() + "/timepoint" + viewId.getTimePointId() + "/s0";

		if ( dataType == DataType.UINT16 )
		{
			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval<UnsignedShortType> sourceGridBlock = Views.offsetInterval(img, gridBlock[0], gridBlock[1]);
			N5Utils.saveNonEmptyBlock(sourceGridBlock, n5, dataset, gridBlock[2], new UnsignedShortType());
		}
		else if ( dataType == DataType.UINT8 )
		{
			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval<UnsignedByteType> sourceGridBlock = Views.offsetInterval(img, gridBlock[0], gridBlock[1]);
			N5Utils.saveNonEmptyBlock(sourceGridBlock, n5, dataset, gridBlock[2], new UnsignedByteType());
		}
		else if ( dataType == DataType.FLOAT32 )
		{
			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval<FloatType> sourceGridBlock = Views.offsetInterval(img, gridBlock[0], gridBlock[1]);
			N5Utils.saveNonEmptyBlock(sourceGridBlock, n5, dataset, gridBlock[2], new FloatType());
		}
		else
		{
			n5.close();
			throw new RuntimeException("Unsupported pixel type: " + dataType );
		}

		System.out.println( "ViewId " + Group.pvid( viewId ) + ", written block: offset=" + Util.printCoordinates( gridBlock[0] ) + ", dimension=" + Util.printCoordinates( gridBlock[1] ) );
	}

	public static void createGroups(
			final N5Writer n5,
			final AbstractSpimData<?> data,
			final Map<Integer, long[]> viewSetupIdToDimensions,
			final int[] blockSize,
			final int[][] downsamplingFactors,
			final Compression compression )
	{
		for ( final Entry< Integer, long[] > viewSetup : viewSetupIdToDimensions.entrySet() )
		{
			final Object type = data.getSequenceDescription().getImgLoader().getSetupImgLoader( viewSetup.getKey() ).getImageType();
			final DataType dataType;
	
			if ( UnsignedShortType.class.isInstance( type ) )
				dataType = DataType.UINT16;
			else if ( UnsignedByteType.class.isInstance( type ) )
				dataType = DataType.UINT8;
			else if ( FloatType.class.isInstance( type ) )
				dataType = DataType.FLOAT32;
			else
				throw new RuntimeException("Unsupported pixel type: " + type.getClass().getCanonicalName() );
	
			// ViewSetupId needs to contain: {"downsamplingFactors":[[1,1,1],[2,2,1]],"dataType":"uint16"}
			final String n5Dataset = "setup" + viewSetup.getKey();
	
			System.out.println( "Creating group: " + "'setup" + viewSetup.getKey() + "'" );
	
			n5.createGroup( n5Dataset );
	
			System.out.println( "setting attributes for '" + "setup" + viewSetup.getKey() + "'");
	
			n5.setAttribute( n5Dataset, "downsamplingFactors", downsamplingFactors );
			n5.setAttribute( n5Dataset, "dataType", dataType );
			n5.setAttribute( n5Dataset, "blockSize", blockSize );
			n5.setAttribute( n5Dataset, "dimensions", viewSetup.getValue() );
			n5.setAttribute( n5Dataset, "compression", compression );
		}
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

	public static ArrayList<long[][]> assembleAllS0Jobs(
			final Collection< ? extends ViewId > viewIds,
			final HashMap< Integer, long[] > viewSetupIdToDimensions,
			final int[] blockSize,
			final int[] computeBlockSize )
	{
		// all blocks (a.k.a. grids) across all ViewId's
		final ArrayList<long[][]> allBlocks = new ArrayList<>();

		for ( final ViewId viewId : viewIds )
		{
			final List<long[][]> grid = Grid.create(
					viewSetupIdToDimensions.get( viewId.getViewSetupId() ),
					computeBlockSize,
					blockSize);

			// add timepointId and ViewSetupId & dimensions to the gridblock
			for ( final long[][] gridBlock : grid )
				allBlocks.add( new long[][]{
					gridBlock[ 0 ].clone(),
					gridBlock[ 1 ].clone(),
					gridBlock[ 2 ].clone(),
					new long[] { viewId.getTimePointId(), viewId.getViewSetupId() },
					viewSetupIdToDimensions.get( viewId.getViewSetupId() ) // TODO: do we need the dimensions?
				});
		}

		return allBlocks;
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
