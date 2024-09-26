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
package net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.io.Files;

import loci.formats.FileStitcher;
import loci.formats.IFormatReader;
import loci.formats.Memoizer;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.util.BioformatsReaderUtils;
import util.ImgLib2Tools;

public class FileMapImgLoaderLOCI2 implements ImgLoader, FileMapGettable
{
	private final Map< ViewId, FileMapEntry > fileMap;
	private final AbstractSequenceDescription<?, ?, ?> sd;
	private boolean allTimepointsInSingleFiles;
	private final File tempDir;
	public boolean zGrouped;

	public FileMapImgLoaderLOCI2(
			final Map< ? extends ViewId, FileMapEntry > fileMap,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		this( fileMap, sequenceDescription, false );
	}

	public FileMapImgLoaderLOCI2( Map< ? extends ViewId, FileMapEntry > fileMap,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription,
			final boolean zGrouped )
	{
		this.fileMap = new HashMap<>();
		this.fileMap.putAll( fileMap );

		this.tempDir = Files.createTempDir();

		this.sd = sequenceDescription;
		this.zGrouped = zGrouped;
		allTimepointsInSingleFiles = true;

		// populate map file -> {time points}
		final Map< File, Set< Integer > > tpsPerFile = new HashMap<>();
		for ( final Map.Entry< ? extends ViewId, FileMapEntry > entry : fileMap.entrySet() )
		{
			final ViewId vid = entry.getKey();
			final File file = entry.getValue().file();
			final Set< Integer > tps = tpsPerFile.computeIfAbsent( file, k -> new HashSet<>() );
			tps.add( vid.getTimePointId() );

			// the current file has more than one time point
			if ( tps.size() > 1 )
			{
				allTimepointsInSingleFiles = false;
				break;
			}
		}

		System.out.println( allTimepointsInSingleFiles );
	}


	@Override
	public SetupImgLoader< ? > getSetupImgLoader(int setupId)
	{
		return new FileMapSetupImgLoaderLOCI2<>(setupId);
	}


	/* (non-Javadoc)
	 * @see spim.fiji.spimdata.imgloaders.filemap2.FileMapGettable#getFileMap()
	 */
	@Override
	public Map< ViewId, FileMapEntry > getFileMap()
	{
		return fileMap;
	}

	public class FileMapSetupImgLoaderLOCI2 <T extends RealType<T> & NativeType< T >> implements SetupImgLoader< T >
	{
		private int setupId;

		public FileMapSetupImgLoaderLOCI2(int setupId)
		{
			this.setupId = setupId;
		}

		private IFormatReader getReader()
		{
			// use a new ImageReader since we might be loading multi-threaded and BioFormats is not thread-save
			// use Memoizer to cache ReaderState for each File on disk
			// see: https://www-legacy.openmicroscopy.org/site/support/bio-formats5.1/developers/matlab-dev.html#reader-performance
			IFormatReader reader = null;
			if (zGrouped)
			{
				final FileStitcher fs = new FileStitcher(true);
				fs.setCanChangePattern( false );
				reader = new Memoizer( fs , Memoizer.DEFAULT_MINIMUM_ELAPSED, tempDir);
			}
			else
			{
				reader = new Memoizer( BioformatsReaderUtils.createImageReaderWithSetupHooks(), Memoizer.DEFAULT_MINIMUM_ELAPSED, tempDir );
			}

			return reader;
		}

		@Override
		public RandomAccessibleInterval< T > getImage( final int timepointId, final ImgLoaderHint... hints)
		{
			final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( new ViewId( timepointId, setupId ) );
			final FileMapEntry imageSource = fileMap.get( vd );

			// TODO: some logging here? (reading angle .. , tp .., ... from file ...)

			final Dimensions size = vd.getViewSetup().getSize();

			final IFormatReader reader = getReader();

			RandomAccessibleInterval< T > img = null;
			try
			{
				img = Cast.unchecked( new VirtualRAIFactoryLOCI().createVirtualCached(
						reader, imageSource.file(), imageSource.series(),
						imageSource.channel(), allTimepointsInSingleFiles ? 0 : timepointId, new UnsignedShortType(), size ) );
			}
			catch ( IncompatibleTypeException e )
			{
				e.printStackTrace();
			}

			return img;
		}

		@Override
		public T getImageType()
		{
			return (T) new UnsignedShortType();

			/*
			final BasicViewDescription< ? > aVd = getAnyPresentViewDescriptionForViewSetup( sd, setupId );
			final Pair< File, Pair< Integer, Integer > > aPair = fileMap.get( aVd );

			final IFormatReader reader = getReader();
			VirtualRAIFactoryLOCI.setReaderFileAndSeriesIfNecessary( reader, aPair.getA(), aPair.getB().getA() );

			if (reader.getPixelType() == FormatTools.UINT8)
				return (T) new UnsignedByteType();
			else if (reader.getPixelType() == FormatTools.UINT16)
				return (T) new UnsignedShortType();
			else if (reader.getPixelType() == FormatTools.INT16)
				return (T) new ShortType();
			else if (reader.getPixelType() == FormatTools.UINT32)
				return (T) new UnsignedIntType();
			else if (reader.getPixelType() == FormatTools.FLOAT)
				return (T) new FloatType();
			return null;
			*/
		}

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
				ImgLoaderHint... hints)
		{
			if ( normalize )
				return ImgLib2Tools.normalizeVirtualRAI( getImage( timepointId, hints ) );
			else
				return ImgLib2Tools.convertVirtualRAI( getImage( timepointId, hints ) );
		}

		@Override
		public Dimensions getImageSize(int timepointId)
		{
			// NB: in all current uses we should have size information in the sd
			BasicViewDescription< ? > vd = sd.getViewDescriptions().get( new ViewId( timepointId, setupId ) );
			return vd.getViewSetup().getSize();
		}

		@Override
		public VoxelDimensions getVoxelSize(int timepointId)
		{
			// NB: in all current uses we should have size information in the sd
			BasicViewDescription< ? > vd = sd.getViewDescriptions().get( new ViewId( timepointId, setupId ) );
			return vd.getViewSetup().getVoxelSize();
		}

	}

	/**
	 * copy src to dest
	 * @param src : source, will not be modified
	 * @param dest : destiantion, will be modified
	 * @param <T> pixel type source
	 * @param <S> pixel type destination
	 */
	public static <T extends RealType<T>, S extends RealType<S>> void copy(RandomAccessible< T > src, RandomAccessibleInterval< S > dest)
	{
		final Cursor< S > destCursor = Views.iterable( dest ).localizingCursor();
		final RandomAccess< T > srcRA = src.randomAccess();

		while (destCursor.hasNext())
		{
			destCursor.fwd();
			srcRA.setPosition( destCursor );
			destCursor.get().setReal( srcRA.get().getRealDouble() );
		}

	}

	public static BasicViewDescription< ? > getAnyPresentViewDescriptionForViewSetup(AbstractSequenceDescription< ?, ?, ? > sd, int viewSetupId)
	{
		for (final ViewId vid : sd.getViewDescriptions().keySet())
			if (vid.getViewSetupId() == viewSetupId)
				if (!sd.getMissingViews().getMissingViews().contains( vid ))
					return sd.getViewDescriptions().get( vid );

		return null;
	}

}
