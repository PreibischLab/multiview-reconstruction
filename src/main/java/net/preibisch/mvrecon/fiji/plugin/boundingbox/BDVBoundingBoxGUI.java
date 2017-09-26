package net.preibisch.mvrecon.fiji.plugin.boundingbox;

import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxBigDataViewer;

import mpicbg.spim.data.sequence.ViewId;

public class BDVBoundingBoxGUI extends BoundingBoxGUI
{
	public BDVBoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	protected boolean allowModifyDimensions()
	{
		return false;
	}

	@Override
	protected boolean setUpDefaultValues( final int[] rangeMin, final int[] rangeMax )
	{
		if ( !findRange( spimData, viewIdsToProcess, rangeMin, rangeMax ) )
			return false;

		final BoundingBox bb = new BoundingBoxBigDataViewer( spimData, viewIdsToProcess ).estimate( "temp" );

		this.min = bb.getMin().clone();
		this.max = bb.getMax().clone();

		if ( defaultMin == null )
			defaultMin = min.clone();

		if ( defaultMax == null )
			defaultMax = max.clone();

		return true;
	}

	@Override
	public BoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new BDVBoundingBoxGUI( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Define using the BigDataViewer interactively";
	}

	
}
