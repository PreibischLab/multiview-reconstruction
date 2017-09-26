package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.icp;

import mpicbg.models.Model;

public class IterativeClosestPointParameters
{
	public static double maxDistance = 5;
	public static int maxIterations = 100;

	private double d = 5;
	private int maxIt = 100;

	private Model< ? > model = null;

	public IterativeClosestPointParameters( final Model< ? > model, final double maxDistance, final int maxIterations )
	{
		this.model = model;
		this.d = maxDistance;
		this.maxIt = maxIterations;
	}

	public IterativeClosestPointParameters( final Model< ? > model )
	{
		this( model, maxDistance, maxIterations );
	}

	public Model< ? > getModel() { return model.copy(); }
	public double getMaxDistance() { return d; }
	public int getMaxNumIterations() { return maxIt; }
}
