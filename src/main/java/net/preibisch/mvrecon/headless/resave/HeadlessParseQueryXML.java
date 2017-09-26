package net.preibisch.mvrecon.headless.resave;

import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;

/**
 * Parse and load xml file
 * For the cluster process, it uses clusterExtention.
 */
public class HeadlessParseQueryXML extends GenericLoadParseQueryXML<SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader, XmlIoSpimData2> {
    public HeadlessParseQueryXML() {
        super(new XmlIoSpimData2(""));
    }

    public boolean loadXML(final String xmlFilename, final boolean useCluster) {
        // try to parse the file anyways
        if (!tryParsing(xmlFilename, true))
            return false;

        // Process attribute choices
//		for ( int i = 0; i < specifyAttributes.size(); ++i )
//		{
//			final String attribute = specifyAttributes.get( i );
//			final int choice = gd.getNextChoiceIndex();
//
//			defaultAttributeChoice.put( attribute, choice );
//			attributeChoice.put( attribute, choice );
//		}

        if (!queryDetails())
            return false;

        if (useCluster)
            this.clusterExt = "job_" + createUniqueName();
        else
            this.clusterExt = "";

        return true;
    }
}
