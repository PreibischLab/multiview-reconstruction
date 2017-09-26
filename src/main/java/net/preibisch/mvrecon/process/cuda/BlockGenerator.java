package net.preibisch.mvrecon.process.cuda;

import java.util.List;

public interface BlockGenerator< T extends Block >
{
	/**
	 * Divides an image into blocks
	 * 
	 * @param imgSize - the size of the image
	 * @param kernelSize - the size of the kernel (has to be odd!)
	 * @return array of blocks
	 */
	public List< T > divideIntoBlocks( final long[] imgSize, final long[] kernelSize );
}
