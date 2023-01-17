/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2023 Multiview Reconstruction developers.
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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import bdv.util.ConstantRandomAccessible;
import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ExportLarge2DTIFF implements ImgExport
{
	public static String defaultPath = null;

	public static int defaultChoiceR = 0;
	public static int defaultChoiceG = 1;
	public static int defaultChoiceB = 2;

	File path;
	int choiceR, choiceG, choiceB;
	int numFusionGroups;

	ArrayList< RandomAccessibleInterval< UnsignedByteType > > groups = new ArrayList<>();

	@Override
	public ImgExport newInstance() { return new ExportLarge2DTIFF(); }

	@Override
	public String getDescription() { return "Large 2D-TIFF (supports 8-bit, 2D slices only)"; }

	@Override
	public int[] blocksize() { return new int[] { 128, 128, 1 }; } // ?

	@Override
	public boolean finish() { return true; }

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public <T extends RealType<T> & NativeType<T>> boolean exportImage(
			final RandomAccessibleInterval<T> imgInterval,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group<? extends ViewId> fusionGroup )
	{
		// remember all fusiongroups
		groups.add(Views.zeroMin((RandomAccessibleInterval)(Object)imgInterval) );

		// do nothing until all fusiongroups arrived
		if ( groups.size() < numFusionGroups )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Added fusiongroup & waiting ... ");
			return true;
		}

		// the empty image to choose from
		groups.add( Views.interval( new ConstantRandomAccessible<UnsignedByteType>( new UnsignedByteType(), 3 ), new FinalInterval( imgInterval ) ) );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fusing as Large 2D TIFF:'"+path+"'");

		final RandomAccessibleInterval< UnsignedByteType > virtualR = groups.get( choiceR );
		final RandomAccessibleInterval< UnsignedByteType > virtualG = groups.get( choiceG );
		final RandomAccessibleInterval< UnsignedByteType > virtualB = groups.get( choiceB );

		final RandomAccessibleInterval<ARGBType> rgb =
				Converters.mergeARGB( Views.stack( virtualR, virtualG, virtualB ) , ColorChannelOrder.RGB );

		long time = System.currentTimeMillis();

		try
		{
			final ImageOutputStream ios =
					ImageIO.createImageOutputStream(
							new BufferedOutputStream(
									new FileOutputStream(path)));

			final ImageWriter writer = ImageIO.getImageWritersBySuffix("tif").next();
			writer.setOutput(ios);

			final ImageWriteParam param = writer.getDefaultWriteParam();
			//param.setTilingMode(ImageWriteParam.MODE_DEFAULT);
			param.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
			param.setTiling(blocksize()[ 0 ],  blocksize()[ 1 ], 0, 0);

			final RenderedImage mosaic = new ImgLib2RenderedImage( rgb, new int[] { blocksize()[ 0 ],  blocksize()[ 1 ] } ); // seems like the blocksize does not matter

			writer.write(null, new IIOImage(mosaic, null, null), param);
			writer.dispose();
			ios.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Error writing  Large 2D TIFF file: " + e );
			return false;
		}

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): done. [" + (System.currentTimeMillis() - time ) + " ms]" );

		return true;
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		final Interval imgInterval = fusion.getDownsampledBoundingBox();
		final List< Group< ViewDescription > > fusionGroups = fusion.getFusionGroups();
		this.numFusionGroups = fusionGroups.size();

		if ( imgInterval.dimension( 2 ) > 1 )
		{
			IOFunctions.println( "\nERROR: Only 2D export is supported. You can:" );
			IOFunctions.println( " a) export a single slice of a 3D image by defining a bounding box with size=1 in z (minZ = maxZ)");
			IOFunctions.println( " b) export a 2D BigSticher dataset.");

			return false;
		}

		if ( fusionGroups.size() > 3 )
		{
			IOFunctions.println( "\nERROR: You can select 1-3 fusion groups (e.g. 1-3 channels) at once since we need to convert to RGB for export.");
			return false;
		}

		if ( fusion.getPixelType() != 2 )
		{
			IOFunctions.println( "\nERROR: Only 8-bit export is supported by Large 2D TIFF Export.");
			return false;
		}

		final GenericDialogPlus gd = new GenericDialogPlus( "Large 2D TIFF export" );

		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = fusion.getSpimData().getBasePath().getAbsolutePath();

			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );

			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );

			defaultPath = new File( defaultPath, "fused.tif" ).getAbsolutePath();
		}

		PluginHelper.addSaveAsFileField( gd, "TIFF_file", defaultPath, 80 );

		final String[] choices = new String[ fusionGroups.size() + 1 ];
		choices[ choices.length - 1 ] = "[Empty channel]";

		for ( int i = 0; i < fusionGroups.size(); ++i )
		{
			choices[ i ] = OpenSeaDragon.getNameForFusionGroup( fusionGroups.get( i ), fusion.getSplittingType() );
			if ( choices[ i ] == null )
				return false;
		}

		if ( defaultChoiceR >= choices.length )
			defaultChoiceR = 0;

		if ( defaultChoiceG >= choices.length )
			defaultChoiceG = 0;

		if ( defaultChoiceB >= choices.length )
			defaultChoiceB = 0;

		gd.addChoice( "Red channel", choices, choices[defaultChoiceR] );
		gd.addChoice( "Green channel", choices, choices[defaultChoiceG] );
		gd.addChoice( "Blue channel", choices, choices[defaultChoiceB] );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		this.path = new File( defaultPath = gd.getNextString() );

		this.choiceR = defaultChoiceR = gd.getNextChoiceIndex();
		this.choiceG = defaultChoiceG = gd.getNextChoiceIndex();
		this.choiceB = defaultChoiceB = gd.getNextChoiceIndex();

		return true;
	}

	public static class ImgLib2RenderedImage implements RenderedImage
	{
		final Interval interval;
		final RandomAccessible<ARGBType> img;
		final int bsX, bsY;

		public ImgLib2RenderedImage( final RandomAccessibleInterval<ARGBType> img, final int[] blockSize )
		{
			this.interval = new FinalInterval( img.dimensionsAsLongArray() );
			this.img = Views.extendZero( Views.zeroMin( img ) ); // for simplicity for now zero-min
			this.bsX = blockSize[ 0 ];
			this.bsY = blockSize[ 1 ];
		}

		@Override
		public Raster getData( final Rectangle rectangle )
		{
			final Interval interval = new FinalInterval(
					new long[] {rectangle.x, rectangle.y},
					new long[] {rectangle.x + rectangle.width - 1, rectangle.y + rectangle.height - 1 } );
			final RandomAccessibleInterval<ARGBType> block = Views.zeroMin( Views.interval( img, interval ) );

			final BufferedImage bi = new BufferedImage( rectangle.width, rectangle.height, BufferedImage.TYPE_3BYTE_BGR );
			final Cursor<ARGBType> c = Views.flatIterable( block ).cursor();

			for ( int y = 0; y < rectangle.height; ++y )
				for ( int x = 0; x < rectangle.width; ++x )
					bi.setRGB(x, y, c.next().get() );

			return bi.getRaster();
		}

		@Override
		public ColorModel getColorModel() { return new BufferedImage( 16, 16, BufferedImage.TYPE_3BYTE_BGR ).getColorModel(); }

		@Override
		public SampleModel getSampleModel() { return new BufferedImage( 16, 16, BufferedImage.TYPE_3BYTE_BGR ).getSampleModel(); }

		@Override
		public int getWidth() { return (int)interval.dimension( 0 ); }

		@Override
		public int getHeight() { return (int)interval.dimension( 1 ); }

		@Override
		public int getTileWidth() { return bsX; }
		
		@Override
		public int getTileHeight() { return bsY; }
		
		@Override
		public int getTileGridYOffset() { return 0; }
		
		@Override
		public int getTileGridXOffset() { return 0; }
		
		@Override
		public Raster getTile( final int tileX, final int tileY ) { throw new RuntimeException("not supported."); }

		@Override
		public Vector<RenderedImage> getSources() { throw new RuntimeException("not supported."); }

		@Override
		public String[] getPropertyNames() { throw new RuntimeException("not supported."); }
		
		@Override
		public Object getProperty(String name) { throw new RuntimeException("not supported."); }
		
		@Override
		public int getNumXTiles() { return getWidth() / bsX + (getWidth() % bsX == 0 ? 0 : 1 ); }

		@Override
		public int getNumYTiles() { return getHeight() / bsY + (getHeight() % bsY == 0 ? 0 : 1 ); }
		
		@Override
		public int getMinY() { return (int)interval.min( 1 ); }
		
		@Override
		public int getMinX() {return (int)interval.min( 0 ); }
		
		@Override
		public int getMinTileY() { return 0; }
		
		@Override
		public int getMinTileX() { return 0; }

		@Override
		public Raster getData() { throw new RuntimeException("not supported."); }

		@Override
		public WritableRaster copyData(WritableRaster raster) {throw new RuntimeException("not supported.");}
	}

	public static void main( String[] args )
	{

		try
		{
			final FunctionRandomAccessible<ARGBType> checkerboard = new FunctionRandomAccessible<>(
					2,
					(location, value) -> {
						value.set(
								Math.abs(location.getIntPosition(0)) % 3 +
										(-Math.abs(location.getIntPosition(1))) % 3);
					},
					ARGBType::new);

			final RandomAccessibleInterval<ARGBType> img = Views.interval( checkerboard , new FinalInterval( new long[] { 0, 0 }, new long[] { 50000, 100000}));
			//new ImageJ();
			//ImageJFunctions.show( img );
			//SimpleMultiThreading.threadHaltUnClean();



			final ImageOutputStream ios =
					ImageIO.createImageOutputStream(
							new BufferedOutputStream(
									new FileOutputStream(
											new File("/Users/preibischs/Downloads/test2.tiff"))));

			final ImageWriter writer = ImageIO.getImageWritersBySuffix("tif").next();
			writer.setOutput(ios);

			final ImageWriteParam param = writer.getDefaultWriteParam();
			//param.setTilingMode(ImageWriteParam.MODE_DEFAULT);
			//param.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
			//param.setTiling(128, 128, 0, 0);

			final RenderedImage mosaic = new ImgLib2RenderedImage( img, new int[] { 128, 128 } ); // seems like the blocksize does not matter

			writer.write(null, new IIOImage(mosaic, null, null), param);

			System.out.println( "done" );

		} catch (IOException ex) {

		}
	}
}
