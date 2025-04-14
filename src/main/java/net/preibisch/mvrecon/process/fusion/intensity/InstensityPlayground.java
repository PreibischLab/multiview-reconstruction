package net.preibisch.mvrecon.process.fusion.intensity;

import java.net.URI;
import java.net.URISyntaxException;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class InstensityPlayground {

	public static void main(String[] args) throws URISyntaxException, SpimDataException {
		final URI xml = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(xml);

		System.out.println("spimData = " + spimData);
	}
}
