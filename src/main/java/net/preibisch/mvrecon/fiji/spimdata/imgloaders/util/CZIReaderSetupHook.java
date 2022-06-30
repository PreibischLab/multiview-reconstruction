package net.preibisch.mvrecon.fiji.spimdata.imgloaders.util;

import loci.formats.IFormatReader;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.in.ZeissCZIReader;

public class CZIReaderSetupHook implements BioformatsReaderSetupHook {

	private boolean allowAutostitch = false;
	private boolean relativePositions = true;
	private static CZIReaderSetupHook instance = null;

	private CZIReaderSetupHook() {}

	public static CZIReaderSetupHook getInstance()
	{
		if (instance == null)
		{
			instance = new CZIReaderSetupHook();
		}
		return instance;
	}

	public void setAutoStitch(boolean allowAutostitch) {
		this.allowAutostitch = allowAutostitch;
	}

	public void setRelativePositions(boolean relativePositions)
	{
		this.relativePositions = relativePositions;
	}

	@Override
	public void runSetup(IFormatReader reader) {

		if (!(reader instanceof ZeissCZIReader)) {
			return;
		}

		// disable auto stitching, following solutions by @CellKai and @NicoKiaru in
		// https://forum.image.sc/t/change-in-czi-tile-info-metadata-after-upgrade-to-zeiss-lightsheet-7/49414/15
		MetadataOptions options = reader.getMetadataOptions();
		if (options instanceof DynamicMetadataOptions) {
			((DynamicMetadataOptions) options).setBoolean(
					ZeissCZIReader.ALLOW_AUTOSTITCHING_KEY, allowAutostitch);
			((DynamicMetadataOptions) options).setBoolean(
					ZeissCZIReader.RELATIVE_POSITIONS_KEY, relativePositions);
		} else {
			System.err.println("WARNING: could not set CZI autostitching option.");
		}
		reader.setMetadataOptions(options);
	}

}
