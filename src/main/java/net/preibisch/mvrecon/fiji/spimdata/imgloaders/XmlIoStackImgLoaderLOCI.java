package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;

@ImgLoaderIo( format = "spimreconstruction.stack.loci", type = StackImgLoaderLOCI.class )
public class XmlIoStackImgLoaderLOCI extends XmlIoStackImgLoader< StackImgLoaderLOCI >
{
	@Override
	protected StackImgLoaderLOCI createImgLoader( File path, String fileNamePattern, ImgFactory< ? extends NativeType< ? >> imgFactory, 
			int layoutTP, int layoutChannels, int layoutIllum, int layoutAngles, int layoutTiles,
			AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		return new StackImgLoaderLOCI( path, fileNamePattern, imgFactory, layoutTP, layoutChannels, layoutIllum, layoutAngles, layoutTiles, sequenceDescription );
	}
}
