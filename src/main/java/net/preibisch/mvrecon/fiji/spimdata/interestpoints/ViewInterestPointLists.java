package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.util.HashMap;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Maps from a String label to a list of interest points for a specific viewid
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ViewInterestPointLists extends ViewId
{
	protected final HashMap< String, InterestPointList > lookup;
	
	public ViewInterestPointLists( final int timepointId, final int setupId )
	{
		super( timepointId, setupId );
		
		this.lookup = new HashMap< String, InterestPointList >();
	}
	
	public boolean contains( final String label ) { return lookup.containsKey( label ); }
	public HashMap< String, InterestPointList > getHashMap() { return lookup; }
	public InterestPointList getInterestPointList( final String label ) { return lookup.get( label ); }
	public void addInterestPointList( final String label, final InterestPointList pointList ) { lookup.put( label, pointList ); }
}
