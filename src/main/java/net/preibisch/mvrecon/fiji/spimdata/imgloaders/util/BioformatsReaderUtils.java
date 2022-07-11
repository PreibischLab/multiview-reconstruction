package net.preibisch.mvrecon.fiji.spimdata.imgloaders.util;

import java.util.HashMap;

import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.in.ZeissCZIReader;

public class BioformatsReaderUtils {

	static HashMap<Class<? extends IFormatReader>, BioformatsReaderSetupHook> setupHooks = new HashMap<>();
	static {
		setupHooks.put(ZeissCZIReader.class, CZIReaderSetupHook.getInstance());
	}

	public static ImageReader createImageReaderWithSetupHooks()
	{
		final ImageReader reader = new ImageReader();

		// go through all specific format readers and run setup if necessary
		for(final IFormatReader formatReader : reader.getReaders())
			if (setupHooks.containsKey(formatReader.getClass()))
				setupHooks.get(formatReader.getClass()).runSetup(formatReader);

		return reader;
	}
}
