package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;

public abstract class AbstractImgFactoryImgLoader extends AbstractImgLoader
{
	protected ImgFactory< ? extends NativeType< ? > > imgFactory = null;

	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return imgFactory; }
	public void setImgFactory( final ImgFactory< ? extends NativeType< ? > > imgFactory ) { this.imgFactory = imgFactory; }
}
