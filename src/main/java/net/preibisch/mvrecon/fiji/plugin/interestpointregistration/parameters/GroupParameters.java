package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters;

public class GroupParameters
{
	public static String[] ipGroupChoice = new String[]{
			"Do not group interest points, compute views independently",
			"Group interest points (simply combine all in one virtual view)" };

	public enum InterestpointGroupingType { DO_NOT_GROUP, ADD_ALL };

	public InterestpointGroupingType grouping;
	public double mergeDistance;
}
