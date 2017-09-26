package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.geometrichashing;

import mpicbg.models.Model;

public class GeometricHashingParameters
{
	public static float differenceThreshold = Float.MAX_VALUE;
	public static float ratioOfDistance = 10;
	public static int redundancy = 0;

	protected final float dt, rod;
	protected final int re;
	private Model< ? > model = null;

	public GeometricHashingParameters( final Model< ? > model )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.model = model;
		this.re = redundancy;
	}
	
	public GeometricHashingParameters(
			final Model< ? > model,
			final float differenceThreshold,
			final float ratioOfDistance,
			final int redundancy )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.model = model;
		this.re = redundancy;
	}

	public Model< ? > getModel() { return model.copy(); }
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public int getRedundancy() { return re; }
}
