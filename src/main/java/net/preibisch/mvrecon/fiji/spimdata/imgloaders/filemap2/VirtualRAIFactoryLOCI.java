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
package net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import loci.formats.AxisGuesser;
import loci.formats.FileStitcher;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LegacyStackImgLoaderLOCI;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class VirtualRAIFactoryLOCI
{
	@FunctionalInterface
	interface TriConsumer<A,B,C>
	{
		void accept(A a, B b, C c);
		
		default TriConsumer< A, B, C > andThen(TriConsumer< ? super A, ? super B, ? super C > after)
		{
			Objects.requireNonNull( after );

			return (a, b, c) -> {
				accept( a, b, c );
				after.accept( a, b, c );
			};
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends RealType< T > & NativeType< T >> RandomAccessibleInterval< T > createVirtual(
			final IFormatReader reader,
			final File file,
			final int series,
			final int channel,
			final int timepoint,
			T type,
			Dimensions dim) throws IncompatibleTypeException
	{
		setReaderFileAndSeriesIfNecessary( reader, file, series );

		final boolean isLittleEndian = reader.isLittleEndian();		
		final long[] dims = new long[]{reader.getSizeX(), reader.getSizeY(), reader.getSizeZ()};

		if (dim != null)
			dim.dimensions( dims );

		final int pixelType = reader.getPixelType();
		if (pixelType == FormatTools.UINT8)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedByteType() : type, (t, buf, i) -> {t.setReal( (int) buf[i] & 0xff);} );
		else if (pixelType == FormatTools.UINT16)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValueInt( buf, i*2, isLittleEndian ) );} );
		else if (pixelType == FormatTools.INT16)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new ShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValue( buf, i*2, isLittleEndian ) );} );
		else if (pixelType == FormatTools.UINT32)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedIntType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getIntValue( buf, i*4, isLittleEndian ) );} );
		else if (pixelType == FormatTools.FLOAT)
			return new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new FloatType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getFloatValue( buf, i*4, isLittleEndian ) );} );
		else
			throw new IncompatibleTypeException( this, "cannot create virtual image for this pixel type" );
	}
	
	@SuppressWarnings("unchecked")
	public synchronized <T extends RealType< T > & NativeType< T >> RandomAccessibleInterval< T > createVirtualCached(
			final IFormatReader reader,
			final File file,
			final int series,
			final int channel,
			final int timepoint,
			T type,
			Dimensions dim) throws IncompatibleTypeException
	{
		setReaderFileAndSeriesIfNecessary( reader, file, series );

		final boolean isLittleEndian = reader.isLittleEndian();		
		final long[] dims = new long[]{reader.getSizeX(), reader.getSizeY(), reader.getSizeZ()};

		if (dim != null)
			dim.dimensions( dims );

		final int pixelType = reader.getPixelType();
		
		if (pixelType == FormatTools.UINT8)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedByteType() : type, (t, buf, i) -> {t.setReal( (int) buf[i] & 0xff);} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new UnsignedByteType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.UINT16)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValueInt( buf, i*2, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new UnsignedShortType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.INT16)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new ShortType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getShortValue( buf, i*2, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new ShortType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.UINT32)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new UnsignedIntType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getIntValue( buf, i*4, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE, type == null ? (T) new UnsignedIntType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else if (pixelType == FormatTools.FLOAT)
		{
			RandomAccessibleInterval< T > virtualImg = new VirtualRandomAccessibleIntervalLOCI< T >( reader, file, dims, series, channel, timepoint, type == null ? (T) new FloatType() : type, (t, buf, i) -> {t.setReal( LegacyStackImgLoaderLOCI.getFloatValue( buf, i*4, isLittleEndian ) );} );
			return FusionTools.cacheRandomAccessibleInterval( virtualImg, Integer.MAX_VALUE,  type == null ? (T) new FloatType() : type, new int[] {(int)virtualImg.dimension( 0 ), (int)virtualImg.dimension( 1 ), 1} ) ;
		}
		else
			throw new IncompatibleTypeException( this, "cannot create virtual image for this pixel type: " + pixelType );
	}
	
	/**
	 * ensure that the reader we have is set to the correct file and series
	 * @param reader the reader
	 * @param file the file to point the reader to
	 * @param series the series in the file to point the reader to
	 */
	public static void setReaderFileAndSeriesIfNecessary(final IFormatReader reader, final File file, final int series)
	{

		final boolean isFileStitcher = FileStitcher.class.isInstance( ( (Memoizer) reader).getReader() );
		boolean haveToReadFile = false;
		// did we setId at all?
		haveToReadFile |= (reader.getCurrentFile() == null);

		// we only check whether the set file is the correct one if we have a normal reader with normal filenames
		// FIXME: this would probably crash anyway (also for normal readers) as we setId while reader is not closed
		//    but the way we call it, we never have to re-setID for the reader
		// TODO: investigate
		if (!isFileStitcher)
		{
			// is the reader set to the right file?
			// we check the canonical path of the file, otherwise something /./ would lead to setId being called
			// again even though the correct file is set already
			if (!haveToReadFile)
				try { haveToReadFile |= !(new File(reader.getCurrentFile()).getCanonicalPath().equals( file.getCanonicalPath() ) ); }
				catch (IOException e) { return; }
		}

		if (haveToReadFile)
		{
			try
			{
				reader.setId( file.getAbsolutePath() );

				if ( isFileStitcher )
					( (FileStitcher) ( (Memoizer) reader).getReader() ).setAxisTypes( new int[] {AxisGuesser.Z_AXIS} );
			}
			catch ( FormatException | IOException e )
			{
				e.printStackTrace();
				return;
			}
			reader.setSeries( series );
		}
		else
		{
			if (reader.getSeries() != series)
				reader.setSeries( series );
		}
	}

	/** check if the given reader is set to the given file and series
	 * 
	 * @param reader the reader
	 * @param file the file
	 * @param series the series
	 * @return true or false
	 */
	public static boolean checkReaderFileAndSeries(final IFormatReader reader, final File file, final int series)
	{
		// if we have a fileStitcher, do not check the actual file name
		final boolean isFileStitcher = FileStitcher.class.isInstance( ( (Memoizer) reader).getReader() );
		if (isFileStitcher)
			if (reader.getCurrentFile() == null)
				return false;
			else
				return reader.getSeries() == series;

		if (reader.getCurrentFile() == null || !reader.getCurrentFile().equals( file.getAbsolutePath() ))
			return false;
		else
			return reader.getSeries() == series;
	}
	
	public  static <T extends RealType<T> & NativeType< T > > void main(String[] args)
	{
		RandomAccessibleInterval< T > img = null;
		ImageReader reader = new ImageReader();
		try
		{
		img = new VirtualRAIFactoryLOCI().createVirtualCached( reader, new File( "/Users/david/Desktop/2ch2ill2angle.czi" ), 0, 2, 0 , (T) new DoubleType(), null);
		}
		catch ( IncompatibleTypeException e )
		{
			e.printStackTrace();
		}
		
		Img< T > create = new CellImgFactory<T>().create( img, Views.iterable( img ).firstElement().createVariable() );
		
		
		
		System.out.println( Views.iterable( img ).firstElement().getClass());
		ImageJFunctions.show( img, "BDV" );
		System.out.println( reader.getModuloC().step );
	}
}
