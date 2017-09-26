package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

/**
 * A class that organizes all interest point detections of all {@link ViewDescription}s (which extend {@link ViewId})
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ViewInterestPoints
{
	protected final Map< ViewId, ViewInterestPointLists > interestPointCollectionLookup;

	public ViewInterestPoints()
	{
		interestPointCollectionLookup = new HashMap< ViewId, ViewInterestPointLists >();
	}

	public ViewInterestPoints( final Map< ViewId, ViewInterestPointLists > interestPointCollectionLookup )
	{
		this.interestPointCollectionLookup = interestPointCollectionLookup;
	}

	public ViewInterestPoints( final Collection< ViewInterestPointLists > interestPointCollection )
	{
		this.interestPointCollectionLookup = new HashMap< ViewId, ViewInterestPointLists >();
		
		for ( final ViewInterestPointLists v : interestPointCollection )
			this.interestPointCollectionLookup.put( v, v );
	}
	
	public Map< ViewId, ViewInterestPointLists > getViewInterestPoints() { return interestPointCollectionLookup; }
	
	public ViewInterestPointLists getViewInterestPointLists( final int tp, final int setupId )
	{
		return getViewInterestPointLists( new ViewId( tp, setupId ) );
	}

	public ViewInterestPointLists getViewInterestPointLists( final ViewId viewId )
	{
		return interestPointCollectionLookup.get( viewId );
	}
	
	/**
	 * Assembles the {@link ViewInterestPoints} object consisting of a list of {@link ViewInterestPointLists} objects for all {@link ViewDescription}s that are present
	 *
	 * @param viewDescriptions - the view description map
	 *
	 */
	public void createViewInterestPoints( final Map< ViewId, ViewDescription > viewDescriptions )
	{
		for ( final ViewDescription viewDescription : viewDescriptions.values() )
			if ( viewDescription.isPresent() )
				interestPointCollectionLookup.put( viewDescription, new ViewInterestPointLists( viewDescription.getTimePointId(), viewDescription.getViewSetupId() ) );
	}
}
