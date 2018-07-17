package net.preibisch.mvrecon.fiji.plugin.resave;

import java.util.Map;

import bdv.export.ExportMipmapInfo;
import ij.plugin.PlugIn;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.resave.MultiResolutionTools;
import net.preibisch.mvrecon.process.resave.SpimData2Tools;

public class Resave_N5 implements PlugIn
{
	public static void main( final String[] args )
	{
		new Resave_N5().run( null );
	}

	@Override
	public void run( final String arg0 )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "Resaving as N5", "Resave", true, true, true, true, true ) )
			return;

		// load all dimensions if they are not known (required for estimating the mipmap layout)
		if ( SpimData2Tools.loadDimensions( xml.getData(), xml.getViewSetupsToProcess() ) )
		{
			// save the XML again with the dimensions loaded
			SpimData2.saveXML( xml.getData(), xml.getXMLFileName(), xml.getClusterExtension() );
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = MultiResolutionTools.proposeMipmaps( xml.getViewSetupsToProcess() );
		final ExportMipmapInfo max = MultiResolutionTools.getLargestMipMapInfo( perSetupExportMipmapInfo.values() );

		
	}

}
