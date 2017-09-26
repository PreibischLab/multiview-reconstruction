package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.fastrgldm;

import mpicbg.models.Model;

public class FRGLDMParameters
{
	public static float ratioOfDistance = 10;

	public static int redundancy = 1;

	protected final float rod;
	protected final int nn, re;

	private Model< ? > model = null;
	public Model< ? > getModel() { return model.copy(); }

	public FRGLDMParameters( final Model< ? > model )
	{
		this.rod = ratioOfDistance;
		this.nn = 2;
		this.re = redundancy;
		this.model = model;
	}
	
	public FRGLDMParameters( final Model< ? > model, final float ratioOfDistance, final int redundancy )
	{
		this.model = model;
		this.rod = ratioOfDistance;
		this.nn = 2;
		this.re = redundancy;
	}

	public float getRatioOfDistance() { return rod; }
	public int getNumNeighbors() { return nn; }
	public int getRedundancy() { return re; }
}
