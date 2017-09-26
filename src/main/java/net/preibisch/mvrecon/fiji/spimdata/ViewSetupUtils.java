package net.preibisch.mvrecon.fiji.spimdata;

import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;

public class ViewSetupUtils
{
	/**
	 * @param setup - the {@link BasicViewSetup}
	 * @return - the Voxelsize for this {@link ViewSetup} or null if it is not in the XML
	 */
	public static VoxelDimensions getVoxelSize( final BasicViewSetup setup )
	{
		if ( setup.hasVoxelSize() )
			return setup.getVoxelSize();
		else
			return null;
	}

	/**
	 * @param setup - the {@link BasicViewSetup} for which to get the Voxelsize
	 * @param t - the {@link TimePoint} from which to load the Voxelsize
	 * @param loader - the {@link ImgLoader}
	 * @return - the Voxelsize for this {@link ViewSetup}
	 */
	public static VoxelDimensions getVoxelSizeOrLoad( final BasicViewSetup setup, final TimePoint t, final ImgLoader loader )
	{
		if ( setup.hasVoxelSize() )
			return setup.getVoxelSize();
		else
			return loader.getSetupImgLoader( setup.getId() ).getVoxelSize( t.getId() );
	}

	/**
	 * @param setup - the {@link BasicViewSetup}
	 * @return - the image size of this {@link ViewSetup} or null if it is not in the XML
	 */
	public static Dimensions getSize( final BasicViewSetup setup )
	{
		if ( setup.hasSize() )
			return setup.getSize();
		else
			return null;
	}

	/**
	 * @param setup - the {@link BasicViewSetup} for which to get the image size
	 * @param t - the {@link TimePoint} from which to load the image size
	 * @param loader - the {@link ImgLoader}
	 * @return - the image size of this {@link ViewSetup}
	 */
	public static Dimensions getSizeOrLoad( final BasicViewSetup setup, final TimePoint t, final ImgLoader loader )
	{
		if ( setup.hasSize() )
			return setup.getSize();
		else
			return loader.getSetupImgLoader( setup.getId() ).getImageSize( t.getId() );
	}
}
