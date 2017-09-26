package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.rgldm;

import mpicbg.models.Model;

public class RGLDMParameters
{
	public static float differenceThreshold = Float.MAX_VALUE;
	public static float ratioOfDistance = 3; 

	public static int numNeighbors = 3;
	public static int redundancy = 1;
	
	protected final float dt, rod;
	protected final int nn, re;

	private Model< ? > model = null;
	public Model< ? > getModel() { return model.copy(); }

	public RGLDMParameters( final Model< ? > model )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.nn = numNeighbors;
		this.re = redundancy;
		this.model = model;
	}
	
	public RGLDMParameters( final Model< ? > model, final float differenceThreshold, final float ratioOfDistance, final int numNeighbors, final int redundancy )
	{
		this.model = model;
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.nn = numNeighbors;
		this.re = redundancy;
	}
	
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public int getNumNeighbors() { return nn; }
	public int getRedundancy() { return re; }
}
