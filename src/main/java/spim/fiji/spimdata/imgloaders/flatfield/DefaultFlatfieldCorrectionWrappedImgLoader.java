package spim.fiji.spimdata.imgloaders.flatfield;

import java.io.File;
import java.util.Arrays;

import ij.ImageJ;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.process.fusion.FusionTools;


public class DefaultFlatfieldCorrectionWrappedImgLoader extends LazyLoadingFlatFieldCorrectionMap< ImgLoader >
		implements ImgLoader
{
	private ImgLoader wrappedImgLoader;
	private boolean active;
	private boolean cacheResult;

	public DefaultFlatfieldCorrectionWrappedImgLoader(ImgLoader wrappedImgLoader)
	{
		this( wrappedImgLoader, true );
	}

	public DefaultFlatfieldCorrectionWrappedImgLoader(ImgLoader wrappedImgLoader, boolean cacheResult)
	{
		super();
		this.wrappedImgLoader = wrappedImgLoader;
		this.active = true;
		this.cacheResult = cacheResult;
	}

	@Override
	public ImgLoader getWrappedImgLoder()
	{
		return wrappedImgLoader;
	}

	@Override
	public void setActive(boolean active)
	{
		this.active = active;
	}

	@Override
	public boolean isActive()
	{
		return active;
	}

	@Override
	public SetupImgLoader< ? > getSetupImgLoader(int setupId)
	{
		return new DefaultFlatfieldCorrectionWrappedSetupImgLoader<>( setupId );
	}

	class DefaultFlatfieldCorrectionWrappedSetupImgLoader<T extends RealType< T > & NativeType< T >>
			implements SetupImgLoader< T >
	{
		private final int setupId;

		DefaultFlatfieldCorrectionWrappedSetupImgLoader(int setupId)
		{
			this.setupId = setupId;
		}

		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, ImgLoaderHint... hints)
		{
			if (!active)
				return (RandomAccessibleInterval< T >) wrappedImgLoader.getSetupImgLoader( setupId ).getImage( timepointId,
						hints );

			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval< T > rai = FlatFieldCorrectedRandomAccessibleIntervals.create(
					(RandomAccessibleInterval< T >) wrappedImgLoader.getSetupImgLoader( setupId ).getImage( timepointId,
							hints ),
					getBrightImg( new ViewId( timepointId, setupId ) ),
					getDarkImg( new ViewId( timepointId, setupId ) ) );

			if ( cacheResult )
			{
				final int[] cellSize = new int[rai.numDimensions()];
				Arrays.fill( cellSize, 1 );
				for ( int d = 0; d < rai.numDimensions() - 1; d++ )
					cellSize[d] = (int) rai.dimension( d );
				return FusionTools.cacheRandomAccessibleInterval( rai, Long.MAX_VALUE,
						Views.iterable( rai ).firstElement().createVariable(), cellSize );
			}
			else
				return rai;

		}

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
				ImgLoaderHint... hints)
		{
			if (!active)
				return (RandomAccessibleInterval< FloatType >) wrappedImgLoader.getSetupImgLoader( setupId ).getFloatImage( timepointId,
						false, hints );

			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval< FloatType > rai = FlatFieldCorrectedRandomAccessibleIntervals.create(
					(RandomAccessibleInterval< T >) wrappedImgLoader.getSetupImgLoader( setupId ).getImage( timepointId,
							hints ),
					getBrightImg( new ViewId( timepointId, setupId ) ),
					getDarkImg( new ViewId( timepointId, setupId ) ), new FloatType() );

			if ( normalize )
			{
				final VirtuallyNormalizedRandomAccessibleInterval< FloatType > raiNormalized = new VirtuallyNormalizedRandomAccessibleInterval<>(
						rai );
				if ( cacheResult )
				{
					final int[] cellSize = new int[raiNormalized.numDimensions()];
					Arrays.fill( cellSize, 1 );
					for ( int d = 0; d < raiNormalized.numDimensions() - 1; d++ )
						cellSize[d] = (int) raiNormalized.dimension( d );
					return FusionTools.cacheRandomAccessibleInterval( raiNormalized, Long.MAX_VALUE,
							Views.iterable( rai ).firstElement().createVariable(), cellSize );
				}
				else
					return raiNormalized;
			}
			else
			{
				if ( cacheResult )
				{
					final int[] cellSize = new int[rai.numDimensions()];
					Arrays.fill( cellSize, 1 );
					for ( int d = 0; d < rai.numDimensions() - 1; d++ )
						cellSize[d] = (int) rai.dimension( d );
					return FusionTools.cacheRandomAccessibleInterval( rai, Long.MAX_VALUE,
							Views.iterable( rai ).firstElement().createVariable(), cellSize );
				}
				else
					return rai;
			}
		}

		@Override
		public T getImageType()
		{
			@SuppressWarnings("unchecked")
			final T res = (T) wrappedImgLoader.getSetupImgLoader( setupId ).getImageType();
			return res;
		}

		@Override
		public Dimensions getImageSize(int timepointId)
		{
			return wrappedImgLoader.getSetupImgLoader( setupId ).getImageSize( timepointId );
		}

		@Override
		public VoxelDimensions getVoxelSize(int timepointId)
		{
			return wrappedImgLoader.getSetupImgLoader( setupId ).getVoxelSize( timepointId );
		}
	}

	public static void main(String[] args)
	{
		GenericLoadParseQueryXML< SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader, XmlIoSpimData2 > lpq = new GenericLoadParseQueryXML<>(
				new XmlIoSpimData2( "" ) );
		lpq.queryXML();
		SpimData2 data = lpq.getData();

		ImgLoader il = data.getSequenceDescription().getImgLoader();
		DefaultFlatfieldCorrectionWrappedImgLoader ffcil = new DefaultFlatfieldCorrectionWrappedImgLoader( il );
		ffcil.setDarkImage( new ViewId( 0, 0 ), new File( "/Users/david/desktop/ff.tif" ) );

		data.getSequenceDescription().setImgLoader( ffcil );

		new ImageJ();

		RandomAccessibleInterval< FloatType > image = data.getSequenceDescription().getImgLoader()
				.getSetupImgLoader( 0 ).getFloatImage( 0, false );

		RandomAccessibleInterval< FloatType > downsampleBlock = MultiResolutionFlatfieldCorrectionWrappedImgLoader
				.downsampleHDF5( image, new int[] { 3, 3, 2 } );
		ImageJFunctions.show( downsampleBlock, "" );

	}

	@Override
	public boolean isCached()
	{
		return cacheResult;
	}

	@Override
	public void setCached(boolean cached)
	{
		cacheResult = cached;
	}

}
