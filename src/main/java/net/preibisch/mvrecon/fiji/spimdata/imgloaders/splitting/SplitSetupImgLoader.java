package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgLoader;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class SplitSetupImgLoader< T > implements SetupImgLoader< T >
{
	final SetupImgLoader< T > underlyingSetupImgLoader;
	final Interval interval;
	final Dimensions size;

	public SplitSetupImgLoader( final SetupImgLoader< T > underlyingSetupImgLoader, final Interval interval )
	{
		this.underlyingSetupImgLoader = underlyingSetupImgLoader;
		this.interval = interval;

		final long[] dim = new long[ interval.numDimensions() ];
		interval.dimensions( dim );

		this.size = new FinalDimensions( dim );
	}

	@Override
	public RandomAccessibleInterval< T > getImage( final int timepointId, final ImgLoaderHint... hints )
	{
		return Views.zeroMin( Views.interval( underlyingSetupImgLoader.getImage( timepointId, hints ), interval ) );
	}

	@Override
	public T getImageType()
	{
		return underlyingSetupImgLoader.getImageType();
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, final boolean normalize, final ImgLoaderHint... hints )
	{
		final RandomAccessibleInterval< FloatType > img = Views.zeroMin( Views.interval( underlyingSetupImgLoader.getFloatImage( timepointId, false, hints ), interval ) );

		// TODO: this is stupid, remove capablitity to get FloatType images!
		if ( normalize )
		{
			return AbstractImgLoader.normalizeVirtual( img );
		}
		else
		{
			return img;
		}
	}

	@Override
	public Dimensions getImageSize( final int timepointId )
	{
		return size;
	}

	@Override
	public VoxelDimensions getVoxelSize( final int timepointId )
	{
		return underlyingSetupImgLoader.getVoxelSize( timepointId );
	}
}
