package net.preibisch.mvrecon.fiji.spimdata.imgloaders.util;

import loci.formats.IFormatReader;

public interface BioformatsReaderSetupHook {
	public void runSetup(IFormatReader reader);
}
