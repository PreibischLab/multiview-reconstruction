package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;

public class StackImgLoaderLOCI extends StackImgLoader< LegacyStackImgLoaderLOCI >
{
	public StackImgLoaderLOCI(
			final File path, final String fileNamePattern, final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final int layoutTP, final int layoutChannels, final int layoutIllum, final int layoutAngles, final int layoutTiles,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super( new LegacyStackImgLoaderLOCI( path, fileNamePattern, imgFactory, layoutTP, layoutChannels, layoutIllum, layoutAngles, layoutTiles, sequenceDescription ) );
	}
}
