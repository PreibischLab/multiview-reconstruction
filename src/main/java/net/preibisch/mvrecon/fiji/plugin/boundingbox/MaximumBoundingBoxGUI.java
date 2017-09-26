package net.preibisch.mvrecon.fiji.plugin.boundingbox;

import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

import mpicbg.spim.data.sequence.ViewId;

public class MaximumBoundingBoxGUI extends BoundingBoxGUI
{

	public MaximumBoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	protected boolean setUpDefaultValues( final int[] rangeMin, final int[] rangeMax )
	{
		if ( !findRange( spimData, viewIdsToProcess, rangeMin, rangeMax ) )
			return false;

		this.min = rangeMin.clone();
		this.max = rangeMax.clone();

		if ( defaultMin == null )
			defaultMin = min.clone();

		if ( defaultMax == null )
			defaultMax = max.clone();

		for ( int d = 0; d < this.min.length; ++d )
		{
			min[ d ] = defaultMin[ d ];
			max[ d ] = defaultMax[ d ];
		}

		return true;
	}

	@Override
	public BoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new MaximumBoundingBoxGUI( spimData, viewIdsToProcess );
	}

	@Override
	protected boolean allowModifyDimensions()
	{
		return true;
	}

	@Override
	public String getDescription()
	{
		return "Maximal Bounding Box spanning all transformed views";
	}
}
