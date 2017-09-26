package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.legacy.LegacyImgLoaderWrapper;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public abstract class StackImgLoader< I extends LegacyStackImgLoader > extends LegacyImgLoaderWrapper< UnsignedShortType, I >
{
	public StackImgLoader( I legacyImgLoader )
	{
		super( legacyImgLoader );
	}

	public File getPath() { return legacyImgLoader.path; }
	public String getFileNamePattern() { return legacyImgLoader.fileNamePattern; }
	public int getLayoutTimePoints() { return legacyImgLoader.layoutTP; }
	public int getLayoutChannels() { return legacyImgLoader.layoutChannels; }
	public int getLayoutIlluminations() { return legacyImgLoader.layoutIllum; }
	public int getLayoutAngles() { return legacyImgLoader.layoutAngles; }
	public int getLayoutTiles() { return legacyImgLoader.layoutTiles; }
	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return legacyImgLoader.getImgFactory(); }

	@Override
	public String toString() {
		return legacyImgLoader.toString();
	}
}
