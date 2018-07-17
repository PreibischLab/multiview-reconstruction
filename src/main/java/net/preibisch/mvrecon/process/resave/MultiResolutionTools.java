package net.preibisch.mvrecon.process.resave;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;

public class MultiResolutionTools
{
	public static ExportMipmapInfo getLargestMipMapInfo( final Collection< ExportMipmapInfo > mipmapinfo )
	{
		int maxNumLevels = -1;
		ExportMipmapInfo max = null;

		for ( final ExportMipmapInfo mm : mipmapinfo )
		{
			if ( mm.getNumLevels() > maxNumLevels )
			{
				maxNumLevels = mm.getNumLevels();
				max = mm;
			}
		}

		return max;
	}

	public static Map< Integer, ExportMipmapInfo > proposeMipmaps( final List< ? extends BasicViewSetup > viewsetups )
	{
		final HashMap< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
		for ( final BasicViewSetup setup : viewsetups )
			perSetupExportMipmapInfo.put( setup.getId(), ProposeMipmaps.proposeMipmaps( setup ) );
		return perSetupExportMipmapInfo;
	}

	public static Map< Integer, ExportMipmapInfo > getPerSetupExportMipmapInfo( final AbstractSpimData< ? > spimData, final MultiResolutionParameters params )
	{
		if ( params.setMipmapManual )
		{
			final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
			final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( params.resolutions, params.subdivisions );
			for ( final BasicViewSetup setup : spimData.getSequenceDescription().getViewSetupsOrdered() )
				perSetupExportMipmapInfo.put( setup.getId(), mipmapInfo );
			return perSetupExportMipmapInfo;
		}
		else
			return ProposeMipmaps.proposeMipmaps( spimData.getSequenceDescription() );
	}

}
