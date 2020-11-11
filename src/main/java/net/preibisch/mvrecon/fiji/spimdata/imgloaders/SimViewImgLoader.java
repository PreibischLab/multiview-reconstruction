package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import net.preibisch.mvrecon.fiji.datasetmanager.SimViewMetaData;
import net.preibisch.mvrecon.fiji.datasetmanager.SimViewMetaData.Pattern;

public class SimViewImgLoader implements ImgLoader
{
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
	final File expDir;
	final String filePattern;
	final Pattern patternParser;
	final int type;
	final boolean littleEndian;

	public SimViewImgLoader(
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription,
			final File expDir,
			final String filePattern,
			final int type,
			final boolean littleEndian )
	{
		this.sequenceDescription = sequenceDescription;
		this.expDir = expDir;
		this.filePattern = filePattern;
		this.type = type;
		this.littleEndian = littleEndian;

		this.patternParser = SimViewMetaData.buildPatternParser( filePattern );
	}

	@Override
	public SetupImgLoader<?> getSetupImgLoader( final int setupId )
	{
		return new SimViewSetupImgLoader(
				setupId,
				sequenceDescription,
				expDir,
				filePattern,
				patternParser,
				type,
				littleEndian );
	}
}
