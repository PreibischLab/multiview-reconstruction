package net.preibisch.mvrecon.fiji.spimdata.imgloaders.shearing;

import bdv.ViewerSetupImgLoader;
import bdv.img.n5.N5ImageLoader.SetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;

public class ShearingSetupImgLoader< T extends NativeType< T >, V extends Volatile< T > & NativeType< V > > implements ViewerSetupImgLoader< T, V >, MultiResolutionSetupImgLoader< T >
{
	final SetupImgLoader< T, V > n5SetupLoader;
	final int setupId;

	public ShearingSetupImgLoader( final int setupId, final SetupImgLoader< T, V > n5SetupLoader )
	{
		this.n5SetupLoader = n5SetupLoader;
		this.setupId = setupId;
	}

	@Override
	public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double[][] getMipmapResolutions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AffineTransform3D[] getMipmapTransforms() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int numMipmapLevels() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public RandomAccessibleInterval<T> getImage(int timepointId, ImgLoaderHint... hints) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public T getImageType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public VoxelDimensions getVoxelSize(int timepointId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Dimensions getImageSize(int timepointId, int level) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RandomAccessibleInterval<V> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public V getVolatileImageType() {
		// TODO Auto-generated method stub
		return null;
	}


}
