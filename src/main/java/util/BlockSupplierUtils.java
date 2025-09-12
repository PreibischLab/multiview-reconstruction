package util;

import net.imglib2.Interval;
import net.imglib2.algorithm.blocks.BlockAlgoUtils;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.optional.CacheOptions.CacheType;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.type.NativeType;

public class BlockSupplierUtils
{

	/**
	 * Made to represent a BlockSupplier as a CachedCellImage, dimensions do not have to match, but should
	 * 
	 * for SOFTREF cache call BlockAlgoUtils.cellImg( ... )
	 *
	 * @param <T>
	 * @param blocks
	 * @param dimensions
	 * @param cellDimensions
	 * @param maxCacheSize
	 * @return
	 */
	public static < T extends NativeType< T > > CachedCellImg< T, ? > cellImgBoundedCache(
			final BlockSupplier< T > blocks,
			long[] dimensions,
			final int[] cellDimensions,
			final int maxCacheSize )
	{
		return new ReadOnlyCachedCellImgFactory().create(
				dimensions,
				blocks.getType(),
				BlockAlgoUtils.cellLoader( blocks ),
				ReadOnlyCachedCellImgOptions.options().cellDimensions( cellDimensions ).cacheType( CacheType.BOUNDED ).maxCacheSize( maxCacheSize ) );
	}

	/**
	 * Allows you to copy a subset of a BlockSupplier into an ArrayImg
	 * 
	 * @param <T>
	 * @param blocks
	 * @param interval
	 * @return
	 */
	public static < T extends NativeType< T > > ArrayImg< T, ? > arrayImg(
			final BlockSupplier< T > blocks,
			final Interval interval )
	{
		final ArrayImg< T, ? > img = new ArrayImgFactory<>( blocks.getType() ).create( interval );
		final Object dest = ( ( ArrayDataAccess< ? > ) img.update( null ) ).getCurrentStorageArray();
		blocks.copy( interval, dest );
		return img;
	}

}
