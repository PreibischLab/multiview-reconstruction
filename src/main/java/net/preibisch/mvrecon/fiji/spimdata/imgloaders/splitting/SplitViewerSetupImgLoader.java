package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import bdv.ViewerSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgLoader;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class SplitViewerSetupImgLoader implements ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType >, MultiResolutionSetupImgLoader< UnsignedShortType >
{
	final ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > underlyingSetupImgLoader;
	final Interval interval;
	final Dimensions size;
	final VoxelDimensions voxelDim;
	final int n;

	final double[][] mipmapResolutions;
	final AffineTransform3D[] mipmapTransforms;
	final Dimensions[] sizes;
	final Interval[] scaledIntervals;

	public SplitViewerSetupImgLoader( final ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > underlyingSetupImgLoader, final Interval interval, final VoxelDimensions voxelDim )
	{
		this.underlyingSetupImgLoader = underlyingSetupImgLoader;
		this.interval = interval;
		this.voxelDim = voxelDim;
		this.n = interval.numDimensions();

		final long[] dim = new long[ interval.numDimensions() ];
		interval.dimensions( dim );

		this.size = new FinalDimensions( dim );

		final int levels = underlyingSetupImgLoader.numMipmapLevels();
		this.sizes = new Dimensions[ levels ];
		this.scaledIntervals = new Interval[ levels ];
		this.mipmapResolutions = underlyingSetupImgLoader.getMipmapResolutions();
		this.mipmapTransforms = new AffineTransform3D[ levels ];

		final AffineTransform3D[] oldmipmapTransforms = underlyingSetupImgLoader.getMipmapTransforms();

		// precompute intervals and new mipmaptransforms (because of rounding of interval borders)
		for ( int level = 0; level < levels; ++level )
		{
			final double[] min = new double[ n ];
			final double[] max = new double[ n ];
	
			final long[] minL = new long[ n ];
			final long[] maxL = new long[ n ];
			final long[] size = new long[ n ];

			for ( int d = 0; d < n; ++d )
			{
				min[ d ] = interval.realMin( d ) / mipmapResolutions[ level ][ d ];
				max[ d ] = interval.realMax( d ) / mipmapResolutions[ level ][ d ];

				minL[ d ] = Math.round( Math.floor( min[ d ] ) );
				maxL[ d ] = Math.round( Math.floor( max[ d ] ) );

				size[ d ] = maxL[ d ] - minL[ d ] + 1;
			}

			this.sizes[ level ] = new FinalDimensions( size );
			this.scaledIntervals[ level ] = new FinalInterval( minL, maxL );

			final AffineTransform3D mipMapTransform = oldmipmapTransforms[ level ].copy();

			// the additional downsampling (performed below)
			final AffineTransform3D additonalTranslation = new AffineTransform3D();
			additonalTranslation.set(
					1.0, 0.0, 0.0, (minL[ 0 ] - min[ 0 ]),
					0.0, 1.0, 0.0, (minL[ 1 ] - min[ 1 ]),
					0.0, 0.0, 1.0, (minL[ 2 ] - min[ 2 ]) );
	
			mipMapTransform.concatenate( additonalTranslation );
			this.mipmapTransforms[ level ] = mipMapTransform;
		}
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final int timepointId, final ImgLoaderHint... hints )
	{
		IOFunctions.println( "requesting full size: " );

		return Views.zeroMin( Views.interval( underlyingSetupImgLoader.getImage( timepointId, hints ), interval ) );
	}

	@Override
	public UnsignedShortType getImageType()
	{
		return underlyingSetupImgLoader.getImageType();
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, final boolean normalize, final ImgLoaderHint... hints )
	{
		if ( MultiResolutionSetupImgLoader.class.isInstance( underlyingSetupImgLoader ) )
		{
			final RandomAccessibleInterval< FloatType > img = Views.zeroMin( Views.interval( ((MultiResolutionSetupImgLoader<?>)underlyingSetupImgLoader).getFloatImage( timepointId, false, hints ), interval ) );
	
			// TODO: this is stupid, remove capablitity to get FloatType images!
			if ( normalize )
			{
				final Img< FloatType > img2 = new CellImgFactory<>( new FloatType() ).create( img );
				FusionTools.copyImg( img, img2, null );
				AbstractImgLoader.normalize( img2 );
				return img2;
			}
			else
			{
				return img;
			}
		}
		else
		{
			throw new RuntimeException( "not supported." );
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
		return voxelDim;
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		return Views.zeroMin( Views.interval( underlyingSetupImgLoader.getImage( timepointId, level, hints ), scaledIntervals[ level ] ) );
	}

	@Override
	public RandomAccessibleInterval< VolatileUnsignedShortType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints )
	{
		return Views.zeroMin( Views.interval( underlyingSetupImgLoader.getVolatileImage( timepointId, level, hints ), scaledIntervals[ level ] ) );
	}

	@Override
	public double[][] getMipmapResolutions()
	{
		return mipmapResolutions;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms()
	{
		return mipmapTransforms;
	}

	@Override
	public int numMipmapLevels()
	{
		return underlyingSetupImgLoader.numMipmapLevels();
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( int timepointId,
			int level, boolean normalize, ImgLoaderHint... hints )
	{
		throw new RuntimeException( "not supported." );
	}

	@Override
	public Dimensions getImageSize( final int timepointId, final int level )
	{
		return sizes[ level ];
	}

	@Override
	public VolatileUnsignedShortType getVolatileImageType()
	{
		return underlyingSetupImgLoader.getVolatileImageType();
	}
}
