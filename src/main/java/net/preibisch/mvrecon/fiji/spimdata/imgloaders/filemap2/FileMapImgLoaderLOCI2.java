package net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
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
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LegacyFileMapImgLoaderLOCI;

public class FileMapImgLoaderLOCI2 implements ImgLoader, FileMapGettable
{
	private final HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap;
	private final AbstractSequenceDescription<?, ?, ?> sd;
	private boolean allTimepointsInSingleFiles;
	private final Map< File, IFormatReader > readers;
	
	public FileMapImgLoaderLOCI2(HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap,
			final ImgFactory< ? extends NativeType< ? > > imgFactory, // FIXME: remove this, only here to test quick replacement
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		this.fileMap = fileMap;
		this.sd = sequenceDescription;
		
		this.readers = new HashMap<>();
		
		allTimepointsInSingleFiles = true;
		
		// populate map file -> {time points}
		Map< File, Set< Integer > > tpsPerFile = new HashMap<>();
		for ( BasicViewDescription< ? > vd : fileMap.keySet() )
		{

			final File fileForVd = fileMap.get( vd ).getA();
			if ( !tpsPerFile.containsKey( fileForVd ) )
				tpsPerFile.put( fileForVd, new HashSet<>() );

			tpsPerFile.get( fileForVd ).add( vd.getTimePointId() );

			// the current file has more than one time point
			if ( tpsPerFile.get( fileForVd ).size() > 1 )
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
	public HashMap< BasicViewDescription< ? >, Pair< File, Pair< Integer, Integer > > > getFileMap()
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

		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, ImgLoaderHint... hints)
		{
			final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( new ViewId( timepointId, setupId ) );
			final Pair< File, Pair< Integer, Integer > > imageSource = fileMap.get( vd );

			// TODO: some logging here? (reading angle .. , tp .., ... from file ...)

			final Dimensions size = vd.getViewSetup().getSize();

			if (!readers.containsKey( imageSource.getA() ))
				readers.put( imageSource.getA(), new ImageReader() );
			IFormatReader reader = readers.get( imageSource.getA() );

			RandomAccessibleInterval< T > img = null;
			try
			{
				img = (RandomAccessibleInterval< T >) (Object)new VirtualRAIFactoryLOCI().createVirtualCached(
						reader, imageSource.getA(), imageSource.getB().getA(),
						imageSource.getB().getB(), allTimepointsInSingleFiles ? 0 : timepointId, new UnsignedShortType(), size );
			}
			catch ( IncompatibleTypeException e )
			{
				e.printStackTrace();
			}

			boolean loadCompletelyRequested = false;
			for (ImgLoaderHint hint : hints)
				if (hint == ImgLoaderHints.LOAD_COMPLETELY)
					loadCompletelyRequested = true;

			if (loadCompletelyRequested)
			{
				long numPx = 1;
				for (int d = 0; d < img.numDimensions(); d++)
					numPx *= img.dimension( d );
				
				final ImgFactory< T > imgFactory;
				if (Math.log(numPx) / Math.log( 2 ) < 31)
					imgFactory = new ArrayImgFactory<T>();
				else
					imgFactory = new CellImgFactory<T>();
				
				Img< T > loadedImg = imgFactory.create( img, getImageType() );
				copy(Views.extendZero( img ), loadedImg);
				
				img = loadedImg;
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

			final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( new ViewId( timepointId, setupId ) );
			final Pair< File, Pair< Integer, Integer > > imageSource = fileMap.get( vd );

			final Dimensions size = vd.getViewSetup().getSize();

			// TODO: some logging here? (reading angle .. , tp .., ... from file ...)

			if (!readers.containsKey( imageSource.getA() ))
				readers.put( imageSource.getA(), new ImageReader() );
			IFormatReader reader = readers.get( imageSource.getA() );
			
			RandomAccessibleInterval< FloatType > img = null;
			try
			{
				img = new VirtualRAIFactoryLOCI().createVirtualCached( reader, imageSource.getA(),
						imageSource.getB().getA(), imageSource.getB().getB(),
						allTimepointsInSingleFiles ? 0 : timepointId, new FloatType(), size );
			}
			catch ( IncompatibleTypeException e )
			{
				e.printStackTrace();
			}



			boolean loadCompletelyRequested = false;
			for (ImgLoaderHint hint : hints)
				if (hint == ImgLoaderHints.LOAD_COMPLETELY)
					loadCompletelyRequested = true;

			// we need the whole image in memory if we want to normalize or load completely
			if (normalize || loadCompletelyRequested)
			{
				long numPx = 1;
				for (int d = 0; d < img.numDimensions(); d++)
					numPx *= img.dimension( d );
				
				final ImgFactory< FloatType > imgFactory;
				if (Math.log(numPx) / Math.log( 2 ) < 31)
					imgFactory = new ArrayImgFactory<FloatType>();
				else
					imgFactory = new CellImgFactory<FloatType>();
				
				Img< FloatType > loadedImg = imgFactory.create( img, new FloatType() );
				copy(Views.extendZero( img ), loadedImg);
				
				img = loadedImg;
			}

			if (normalize)
				AbstractImgLoader.normalize( img );

			return img;
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
