package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.centerofmass;

/**
 * Center of Mass (Avg/Median) implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class CenterOfMassParameters
{
	final static String[] centerChoice = new String[]{ "Average", "Median" };
	public static int defaultCenterChoice = 0;
	protected int centerType = 0;

	public CenterOfMassParameters( final int centerType ) { this.centerType = centerType; }

	public int getCenterType() { return centerType; }
}
