package net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.util.HashMap;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import net.imglib2.util.Pair;

public interface FileMapGettable
{

	HashMap< BasicViewDescription< ? >, Pair< File, Pair< Integer, Integer > > > getFileMap();

}