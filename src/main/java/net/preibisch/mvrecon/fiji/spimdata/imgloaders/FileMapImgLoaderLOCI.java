package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;
import java.util.HashMap;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.legacy.LegacyImgLoaderWrapper;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2.FileMapGettable;

public class FileMapImgLoaderLOCI extends LegacyImgLoaderWrapper< UnsignedShortType, LegacyFileMapImgLoaderLOCI > implements FileMapGettable
{

	public FileMapImgLoaderLOCI(
			HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription )
	{
		super( new LegacyFileMapImgLoaderLOCI( fileMap, imgFactory, sequenceDescription ) );
	}

	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return legacyImgLoader.getImgFactory(); }
	public void setImgFactory(ImgFactory< ? extends NativeType< ? > > factory){legacyImgLoader.setImgFactory( factory );}

	@Override
	public String toString() {
		return legacyImgLoader.toString();
	}
	
	@Override
	public HashMap< BasicViewDescription< ? >, Pair< File, Pair< Integer, Integer > > > getFileMap()
	{
		 return ( (LegacyFileMapImgLoaderLOCI) legacyImgLoader ).getFileMap();
	}
	

}
