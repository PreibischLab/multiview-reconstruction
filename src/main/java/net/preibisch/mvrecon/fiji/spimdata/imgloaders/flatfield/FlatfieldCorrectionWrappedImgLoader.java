package net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield;

import java.io.File;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;

public interface FlatfieldCorrectionWrappedImgLoader<IL extends ImgLoader> extends ImgLoader
{
	public IL getWrappedImgLoder();
	public void setActive(boolean active);
	public boolean isActive();
	public void setCached(boolean cached);
	public boolean isCached();
	public void setBrightImage(ViewId vId, File imgFile);
	public void setDarkImage(ViewId vId, File imgFile);
}
