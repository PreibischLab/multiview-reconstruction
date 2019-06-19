package net.preibisch.mvrecon.process.fusion.transformed.nonrigid;

import java.util.ArrayList;

public class NonRigidParameters
{
	protected double alpha = 1.0;
	protected long controlPointDistance = 10;
	protected boolean showDistanceMap = false;
	protected boolean nonRigidAcrossTime = false;
	protected ArrayList< String > labelList = new ArrayList<>();

	public double getAlpha() { return alpha; }
	public long getControlPointDistance() { return controlPointDistance; }
	public boolean showDistanceMap() { return showDistanceMap; }
	public boolean nonRigidAcrossTime() { return nonRigidAcrossTime; }
	public ArrayList< String > getLabels() { return labelList; }
}
