package net.preibisch.mvrecon.fiji.spimdata.imgloaders.n5;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.DoubleArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.BdvFunctions;
import bdv.util.MipmapTransforms;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class N5ImgLoader implements MultiResolutionImgLoader, ViewerImgLoader
{

	private N5Reader n5 = null;
	private final AbstractSequenceDescription<?, ?, ?> sd;
	private final String containerPath;

	public N5ImgLoader(
			final String containerPath,
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription)
	{
		this.containerPath = containerPath;
		this.sd = sequenceDescription;
	}

	public String getContainerPath()
	{
		return new String( containerPath );
	}

	
	@Override
	public N5SetupImgLoader<?, ?> getSetupImgLoader(int setupId)
	{
		return new N5SetupImgLoader<>( setupId );
	}

	//@Override
	public CacheControl getCacheControl()
	{
		// nop cache control, seems to work
		return new CacheControl.Dummy();
	}


	class N5SetupImgLoader <T extends RealType< T > & NativeType< T >, V extends Volatile< T > & NativeType< V > > implements MultiResolutionSetupImgLoader< T >, ViewerSetupImgLoader< T, V >
	{
		
		private final int setupId;
		private final String resolutionsFstring = "/s%02d/resolutions";
		private final String viewSetupFstring = "/t%05d/s%02d";
		private final String scaleFstring = "/%d/cells";
		
		private double[][] resolutions = null;
		
	
		public N5SetupImgLoader(int setupId)
		{
			this.setupId = setupId;
			try
			{
				n5 = new N5FSReader( containerPath );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
	
		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, int level, ImgLoaderHint... hints)
		{
			try
			{
				return N5Utils.open( n5, String.format( viewSetupFstring, timepointId, setupId ) + "/" + String.format( scaleFstring, level ) );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
			return null;
		}
	
		@Override
		public double[][] getMipmapResolutions()
		{
			if (resolutions == null)
			{
	
					try
					{
						final DatasetAttributes datasetAttributes = n5.getDatasetAttributes( String.format( resolutionsFstring, setupId ) );
						final long[] dims = datasetAttributes.getDimensions();
						final long nScales = dims[1];
	
						resolutions = new double[(int) dims[1]][(int) dims[0]];
		
						final double[] data = ((DoubleArrayDataBlock) n5.readBlock( 
									String.format( resolutionsFstring, setupId ),
									datasetAttributes, new long[2] ))
								.getData();
						
						for (int i = 0; i<nScales; i++)
						{
							resolutions[i][0] = data[i*3 + 0];
							resolutions[i][1] = data[i*3 + 1];
							resolutions[i][2] = data[i*3 + 2];
						}
		
					}
					catch ( IOException e )
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
	
			return resolutions;
		}
	
		@Override
		public AffineTransform3D[] getMipmapTransforms()
		{
			if ( resolutions == null )
				getMipmapResolutions();
	
			AffineTransform3D[] transforms = new AffineTransform3D[resolutions.length];
			for (int i = 0; i<resolutions.length; i++)
				transforms[i] = MipmapTransforms.getMipmapTransformDefault( resolutions[i] );
	
			return transforms;
		}
	
		@Override
		public int numMipmapLevels()
		{
			if ( resolutions == null )
				getMipmapResolutions();
	
			return resolutions.length;
		}
	
		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, ImgLoaderHint... hints)
		{
			return getImage( timepointId, 0, hints );
		}
	
		@Override
		public T getImageType()
		{
			try
			{
				return N5Utils.type( n5.getDatasetAttributes( String.format( viewSetupFstring, 0, setupId ) + "/" + String.format( scaleFstring, 0 ) ).getDataType() );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
			return null;
		}
	
		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
				ImgLoaderHint... hints)
		{
			return getFloatImage( timepointId, 0, normalize, hints );
		}
	
		@Override
		public Dimensions getImageSize(int timepointId)
		{
			return getImageSize( timepointId, 0 );
		}
	
		@Override
		public VoxelDimensions getVoxelSize(int timepointId)
		{
			return sd.getViewDescriptions().get( new ViewId( timepointId, setupId ) ).getViewSetup().getVoxelSize();
		}
	
		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, int level, boolean normalize,
				ImgLoaderHint... hints)
		{
			// TODO: super simple, converter-based float loading, can we do it faster?
			RandomAccessibleInterval< T > image = getImage( timepointId, level, hints );
			RandomAccessibleInterval< FloatType > floatImg = Converters.convert( image, (T i, FloatType o) -> o.set( i.getRealFloat() ), new FloatType() );
			if (normalize)
			{
				final FloatType max = new FloatType(-Float.MAX_VALUE);
				for (FloatType t : Views.iterable( floatImg ))
					if (t.get() > max.get())
						max.set( t );
				floatImg = Converters.convert( image,  (i, o) -> o.set( i.getRealFloat() / max.get() ), new FloatType() );
			}
			return floatImg;
		}
	
		@Override
		public Dimensions getImageSize(int timepointId, int level)
		{
			try
			{
				return new FinalDimensions( 
						n5.getDatasetAttributes( 
								String.format( viewSetupFstring, timepointId, setupId ) + "/" + String.format( scaleFstring, level ) 
								).getDimensions()
						);
			}
			catch ( IOException e )
			{
				e.printStackTrace();
				return null;
			}
		}
	
		@Override
		public RandomAccessibleInterval< V > getVolatileImage(int timepointId, int level, ImgLoaderHint... hints)
		{
			try
			{
				RandomAccessibleInterval img = N5Utils.openVolatile( n5, String.format( viewSetupFstring, timepointId, setupId ) + "/" + String.format( scaleFstring, level ));
				return (RandomAccessibleInterval< V >) VolatileViews.wrapAsVolatile( img );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
			return null;
		}
	
		@Override
		public V getVolatileImageType()
		{
			return (V) VolatileTypeMatcher.getVolatileTypeForType( getImageType() ).createVariable();
		}
	
	}

	public static void main(String[] args)
	{
		N5ImgLoader n5ImgLoader = new N5ImgLoader( "/Users/david/Desktop/grid-3d-stitched-h5/dataset.n5", null );
		RandomAccessibleInterval< ? > image0 = n5ImgLoader.getSetupImgLoader( 0 ).getImage( 0, 0, new ImgLoaderHint[0] );
		RandomAccessibleInterval< ? > image1 = n5ImgLoader.getSetupImgLoader( 0 ).getVolatileImage( 0, 1, new ImgLoaderHint[0] );
		BdvFunctions.show( image0, "scale0" );
		BdvFunctions.show( image1, "scale1v" );
	
	}

}
