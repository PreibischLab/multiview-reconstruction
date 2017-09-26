package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.legacy.LegacyImgLoaderWrapper;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class MicroManagerImgLoader extends LegacyImgLoaderWrapper< UnsignedShortType, LegacyMicroManagerImgLoader >
{
	public MicroManagerImgLoader(
			final File mmFile,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription )
	{
		super( new LegacyMicroManagerImgLoader( mmFile, sequenceDescription ) );
	}

	public File getFile() { return legacyImgLoader.getFile(); }

	@Override
	public String toString() {
		return legacyImgLoader.toString();
	}
}
