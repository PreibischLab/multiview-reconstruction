package net.preibisch.mvrecon.process.fusion.nonrigid;

import java.util.Map;

import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

public class IPL
{
	final String label;
	final Map< Integer, InterestPoint > map;

	public IPL( final String label, final Map< Integer, InterestPoint > map )
	{
		this.label = label;
		this.map = map;
	}

	public String getLabel() { return label; }
	public Map< Integer, InterestPoint > getInterestPointMap() { return map; }
}
