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

import loci.formats.FormatException;
import loci.formats.IFormatReader;
import net.imglib2.AbstractInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2.VirtualRAIFactoryLOCI.TriConsumer;

class VirtualRandomAccessibleIntervalLOCI<T extends RealType< T > & NativeType< T >> extends AbstractInterval
		implements RandomAccessibleInterval< T >
{
	private final IFormatReader reader;
	private final File file;
	private final int series;
	private final int channel;
	private final int timepoint;
	private final T type;
	private final TriConsumer< T, byte[], Integer > byteConverter;

	VirtualRandomAccessibleIntervalLOCI(IFormatReader reader, File file, long[] dims, int series, int channel,
			int timepoint, T type, final TriConsumer< T, byte[], Integer > byteConverter)
	{
		super( dims );
		this.reader = reader;
		this.file = file;
		this.series = series;
		this.channel = channel;
		this.timepoint = timepoint;
		this.type = type;
		this.byteConverter = byteConverter;
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new VirtualRandomAccessLOCI();
	}

	@Override
	public RandomAccess< T > randomAccess(Interval interval)
	{
		return randomAccess();
	}

	private class VirtualRandomAccessLOCI extends Point implements RandomAccess< T >
	{

		private byte[] buffer;
		private T type;
		private int currentZ = -1;

		private VirtualRandomAccessLOCI()
		{
			super( 3 );
			this.type = VirtualRandomAccessibleIntervalLOCI.this.type.createVariable();
			buffer = new byte[0];

		}

		private void readIntoBuffer()
		{

			VirtualRAIFactoryLOCI.setReaderFileAndSeriesIfNecessary( reader, file, series );

			int siz = reader.getBitsPerPixel() / 8 * reader.getRGBChannelCount() * reader.getSizeX()
					* reader.getSizeY();
			buffer = new byte[siz];

//			System.out.println( "reading z plane " + position[2] + " from series " + series + " in file " + file.getAbsolutePath() );

			// FIX for XYZ <-> XYT mixup in rare cases
			int actualTP = (!reader.isOrderCertain() && reader.getSizeZ() <= 1 && reader.getSizeT() > 1 ) ? (int) position[2] : timepoint;
			int actualZ = (!reader.isOrderCertain() && reader.getSizeZ() <= 1 && reader.getSizeT() > 1 ) ? timepoint : (int) position[2];

			try
			{
				// the image is RGB -> we have to read bytes for all channels at once?
				if (reader.getRGBChannelCount() == reader.getSizeC())
					reader.openBytes( reader.getIndex( actualZ, 0, actualTP), buffer );
				// normal image -> read specified channel
				else
					reader.openBytes( reader.getIndex( actualZ, channel, actualTP), buffer );
			}
			catch ( FormatException | IOException e )
			{
				e.printStackTrace();
			}
		}

		@Override
		public T get()
		{
			// prevent multithreaded overwriting of buffer
			synchronized ( reader )
			{
				if ( position[2] != currentZ  || !VirtualRAIFactoryLOCI.checkReaderFileAndSeries( reader, file, series ))
				{
					currentZ = (int) position[2];
					readIntoBuffer();
				}

				int rgbOffset = 0;
				if (reader.getRGBChannelCount() == reader.getSizeC())
					rgbOffset = channel * buffer.length / reader.getSizeC();
					
				// pixel index (we do not care about bytesPerPixel here, byteCOnverter should take care of that)
				final int i = (int) (rgbOffset + position[0] + position[1] * VirtualRandomAccessibleIntervalLOCI.this.dimension( 0 ) );
				byteConverter.accept( type, buffer, i );
				return this.type;
			}
		}

		@Override
		public Sampler< T > copy()
		{
			return copyRandomAccess();
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			return new VirtualRandomAccessLOCI();
		}

	}

}
