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
package net.preibisch.mvrecon.process.export;

import static org.janelia.saalfeldlab.n5.spark.downsample.N5DownsamplerSpark.DOWNSAMPLING_FACTORS_ATTRIBUTE_KEY;
import static org.janelia.saalfeldlab.n5.spark.downsample.scalepyramid.N5ScalePyramidSpark.downsampleScalePyramid;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.spark.downsample.N5DownsamplerSpark;
import org.janelia.saalfeldlab.n5.spark.supplier.N5WriterSupplier;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import fiji.util.gui.GenericDialogPlus;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.io.TiffEncoder;
import ij.process.ColorProcessor;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.N5Parameters;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class SaveN5 implements ImgExport, Calibrateable
{
	public static String defaultPath = null;

	public static int defaultNumSparkJobs = Runtime.getRuntime().availableProcessors();
	public static int defaultNumThreads = 1;

	String n5Path;
	String datasetName;
	int numSparkJobs, numThreads;

	String unit = "px";
	double cal = 1.0;

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup )
	{
		return exportImage( img, bb, downsampling, anisoF, title, fusionGroup, Double.NaN, Double.NaN );
	}

	@Override
	public <T extends RealType<T> & NativeType<T>> boolean exportImage(
			final RandomAccessibleInterval<T> img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final double min,
			final double max )
	{
		// do nothing in case the image is null
		if ( img == null )
			return false;

		// cannot use spark for the full resolution image as we cannot serialize the virtual RandomAccessibleInterval<T> img
		saveN5StackMT()

		// but we can write the multi-resolution pyramid using spark
		final SparkConf conf = new SparkConf().setAppName("BigStitcher.SaveN5ScalePyramid");
		final JavaSparkContext sc = new JavaSparkContext(conf);

		// Now that the full resolution image is saved into n5, generate the scale pyramid
		final N5WriterSupplier n5Supplier = () -> new N5FSWriter( options.getN5Path() );

		// Put the downsampling factors into the full resolution
		final N5Writer n5 = new N5FSWriter(options.getN5Path());
		n5.setAttribute( fullScaleName, DOWNSAMPLING_FACTORS_ATTRIBUTE_KEY, options.getDownsamplingFactors() );

		// Remove previous scales if they exist
		File datasetDir = new File(Paths.get(options.getN5Path(), datasetName).toString());
		for(File f : datasetDir.listFiles()) {
			// try/catch to check for int parse errors which happen if this is not a scale dir
			try {
				if (f.isDirectory() && f.getName().startsWith("s") && Integer.parseInt(f.getName().substring(1)) > 0)
					FileUtils.deleteDirectory(f);
			} catch( Exception ignored ) {
			}
		}

		downsampleScalePyramid(
				sc,
				n5Supplier,
				fullScaleName,
				datasetName,
				options.getDownsamplingFactors()
			);



		if ( success )
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saved file " + fileName );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED saving file " + fileName );

		return false;
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Save fused images as N5 volume" );

		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = fusion.getSpimData().getBasePath().getAbsolutePath();

			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );

			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );

			defaultPath += "/fused.n5";
		}

		final long[] dimensions = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
		fusion.getDownsampledBoundingBox().dimensions( dimensions );

		BasicViewSetup outputSetup = new BasicViewSetup( 0, "", new FinalDimensions( dimensions ), new FinalVoxelDimensions( "", 1, 1, 100 ) );
		final ExportMipmapInfo autoMipmapSettings = Resave_HDF5.proposeMipmaps( outputSetup ); //xml.getViewSetupsToProcess() );

		// block size should be bigger than hdf5
		for ( final int[] row : autoMipmapSettings.getSubdivisions() )
		{
			row[ 0 ] = N5Parameters.defaultBlockSizeXY;
			if ( row.length >= 2 ) row[ 1 ] = N5Parameters.defaultBlockSizeXY;
			if ( row.length >= 3 ) row[ 2 ] = N5Parameters.defaultBlockSize;
		}

		gd.addStringField("N5_path", defaultPath, 80 );
		gd.addMessage( "" );
		gd.addStringField( "Subsampling_factors", ProposeMipmaps.getArrayString( autoMipmapSettings.getExportResolutions() ), 80 );
		gd.addStringField( "Block_sizes", ProposeMipmaps.getArrayString( autoMipmapSettings.getSubdivisions() ), 80 );
		gd.addMessage( "" );
		gd.addNumericField( "Number_of_spark_jobs (CPUs:" + Runtime.getRuntime().availableProcessors() + ")", defaultNumSparkJobs, 0 );
		gd.addNumericField( "Number_of_threads_per_spark_job (CPUs:" + Runtime.getRuntime().availableProcessors() + ")", defaultNumThreads, 0 );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		this.n5Path = defaultPath = gd.getNextString().trim();

		final String subsampling = gd.getNextString();
		final String chunkSizes = gd.getNextString();

		this.numSparkJobs = defaultNumSparkJobs = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );
		this.numThreads = defaultNumThreads = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );

		return true;
	}

	@Override
	public ImgExport newInstance() { return new SaveN5(); }

	@Override
	public String getDescription() { return "Save as N5 volume"; }

	@Override
	public boolean finish()
	{
		// nothing to do
		return false;
	}

	@Override
	public void setCalibration( final double pixelSize, final String unit )
	{
		this.cal = pixelSize;
		this.unit = unit;
	}

	@Override
	public String getUnit() { return unit; }

	@Override
	public double getPixelSize() { return cal; }
	
	public static final < T extends RealType< T> > void saveN5StackMT(
			final RandomAccessibleInterval< T > fused,
			final String n5Path,
			final String datasetName,
			final long[] min,
			final long[] size,
			final int[] blockSize ) throws IOException
	{

		final N5Writer n5 = new N5FSWriter(n5Path);

		n5.createDataset(
				datasetName,
				size,
				blockSize,
				DataType.UINT8,
				new GzipCompression());

		final JavaRDD<long[][]> rdd = sc.parallelize(
				Grid.create(
						new long[] {
								size[0],
								size[1],
								size[2] },
						blockSize));

		// can't serialize the randomaccessible interval!!!
		rdd.foreach(gridBlock -> {

			Views.offsetInterval( fused, offset, gridBlock[1] );

			/* assume we can fit it in an array */
			final ArrayImg<UnsignedByteType, ByteArray> block = ArrayImgs.unsignedBytes(gridBlock[1]);

			for (int z = 0; z < block.dimension(2); ++z)
			{

				//final BufferedImage image = renderImage(

				final IntervalView<UnsignedByteType> outSlice = Views.hyperSlice(block, 2, z);
				@SuppressWarnings({ "unchecked" })
				final IterableInterval<UnsignedByteType> inSlice = Views
						.flatIterable(
								Views.interval(
										ArrayImgs.unsignedBytes(
												(byte[])new ColorProcessor(image).convertToByteProcessor().getPixels(),
												image.getWidth(),
												image.getHeight()),
										outSlice));

				final Cursor<UnsignedByteType> in = inSlice.cursor();
				final Cursor<UnsignedByteType> out = outSlice.cursor();
				while (out.hasNext())
					out.next().set(in.next());
			}

			final N5FSWriter n5Writer = new N5FSWriter(n5Path);
			N5Utils.saveNonEmptyBlock(block, n5Writer, datasetName, gridBlock[2], new UnsignedByteType(0));
		});
	}

	/**
	 *
	 *
	 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
	 */
	public static class Grid
	{
		private Grid() {}

		/**
		 * Create a {@link List} of grid blocks that, for each grid cell, contains
		 * the world coordinate offset, the size of the grid block, and the
		 * grid-coordinate offset.
		 *
		 * @param dimensions
		 * @param blockSize
		 * @return
		 */
		public static List<long[][]> create(
				final long[] dimensions,
				final int[] blockSize) {

			return create(dimensions, blockSize, blockSize);
		}

		/**
		 * Create a {@link List} of grid blocks that, for each grid cell, contains
		 * the world coordinate offset, the size of the grid block, and the
		 * grid-coordinate offset.  The spacing for input grid and output grid
		 * are independent, i.e. world coordinate offsets and cropped block-sizes
		 * depend on the input grid, and the grid coordinates of the block are
		 * specified on an independent output grid.  It is assumed that
		 * gridBlockSize is an integer multiple of outBlockSize.
		 *
		 * @param dimensions
		 * @param gridBlockSize
		 * @param outBlockSize
		 * @return
		 */
		public static List<long[][]> create(
				final long[] dimensions,
				final int[] gridBlockSize,
				final int[] outBlockSize) {
	
			final int n = dimensions.length;
			final ArrayList<long[][]> gridBlocks = new ArrayList<>();
	
			final long[] offset = new long[n];
			final long[] gridPosition = new long[n];
			final long[] longCroppedGridBlockSize = new long[n];
			for (int d = 0; d < n;) {
				cropBlockDimensions(dimensions, offset, outBlockSize, gridBlockSize, longCroppedGridBlockSize, gridPosition);
					gridBlocks.add(
							new long[][]{
								offset.clone(),
								longCroppedGridBlockSize.clone(),
								gridPosition.clone()
							});
	
				for (d = 0; d < n; ++d) {
					offset[d] += gridBlockSize[d];
					if (offset[d] < dimensions[d])
						break;
					else
						offset[d] = 0;
				}
			}
			return gridBlocks;
		}
	
		/**
		 * Crops the dimensions of a {@link DataBlock} at a given offset to fit
		 * into and {@link Interval} of given dimensions.  Fills long and int
		 * version of cropped block size.  Also calculates the grid raster position
		 * assuming that the offset divisible by block size without remainder.
		 *
		 * @param max
		 * @param offset
		 * @param blockSize
		 * @param croppedBlockSize
		 * @param intCroppedBlockDimensions
		 * @param gridPosition
		 */
		static void cropBlockDimensions(
				final long[] dimensions,
				final long[] offset,
				final int[] outBlockSize,
				final int[] blockSize,
				final long[] croppedBlockSize,
				final long[] gridPosition) {
	
			for (int d = 0; d < dimensions.length; ++d) {
				croppedBlockSize[d] = Math.min(blockSize[d], dimensions[d] - offset[d]);
				gridPosition[d] = offset[d] / outBlockSize[d];
			}
		}
	}
}
