package net.preibisch.mvrecon.process.interestpointremoval;

public class ThinOutParameters
{
	public String label, newLabel;
	public double lowerThreshold, upperThreshold;
	public boolean keep;
	
	public String getLabel() { return label; }
	public String getNewLabel() { return newLabel; }
	public boolean keepRange() { return keep; }
	public double getMin() { return lowerThreshold; }
	public double getMax() { return upperThreshold; }
}
