package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;
import java.util.List;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.legacy.LegacyImgLoaderWrapper;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class DHMImgLoader extends LegacyImgLoaderWrapper< UnsignedShortType, LegacyDHMImgLoader >
{
	public DHMImgLoader(
			final File directory,
			final String stackDir,
			final String amplitudeDir,
			final String phaseDir,
			final List< String > timepoints,
			final List< String > zPlanes,
			final String extension,
			final int ampChannelId,
			final int phaseChannelId,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sd )
	{
		super( new LegacyDHMImgLoader( directory, stackDir, amplitudeDir, phaseDir, timepoints, zPlanes, extension, ampChannelId, phaseChannelId, sd ) );
	}

	public String getStackDir() { return legacyImgLoader.getStackDir(); }
	public String getAmplitudeDir() { return legacyImgLoader.getAmplitudeDir(); }
	public String getPhaseDir() { return legacyImgLoader.getPhaseDir(); }
	public List< String > getZPlanes() { return legacyImgLoader.getZPlanes(); }
	public List< String > getTimepoints() { return legacyImgLoader.getTimepoints(); }
	public int getAmpChannelId() { return legacyImgLoader.getAmpChannelId(); }
	public int getPhaseChannelId() { return legacyImgLoader.getPhaseChannelId(); }
	public String getExt() { return legacyImgLoader.getExt(); }
}
