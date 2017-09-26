package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RANSACParameters
{
	public static final String[] ransacChoices = new String[]{ "Fast", "Normal", "Thorough", "Very thorough", "Ridiculous" };
	public static final int[] ransacChoicesIterations = new int[]{ 1000, 10000, 100000, 1000000, 10000000 };

	public static float max_epsilon = 5;
	public static float min_inlier_ratio = 0.1f;
	public static int num_iterations = 10000;
	public static float min_inlier_factor = 3f;
	
	protected float maxEpsilon, minInlierRatio, minInlierFactor;
	protected int numIterations;

	public RANSACParameters( final float maxEpsilon, final float minInlierRatio, final float minInlierFactor, final int numIterations )
	{
		this.maxEpsilon = maxEpsilon;
		this.minInlierRatio = minInlierRatio;
		this.minInlierFactor = minInlierFactor;
		this.numIterations = numIterations;
	}

	public RANSACParameters()
	{
		this.maxEpsilon = max_epsilon;
		this.numIterations = num_iterations;
		this.minInlierRatio = min_inlier_ratio;
		this.minInlierFactor = min_inlier_factor;
	}

	public float getMaxEpsilon() { return maxEpsilon; }
	public float getMinInlierRatio() { return minInlierRatio; }
	public float getMinInlierFactor() { return minInlierFactor; }
	public int getNumIterations() { return numIterations; }

	public RANSACParameters setMaxEpsilon( final float maxEpsilon ) { this.maxEpsilon = maxEpsilon; return this; }
	public RANSACParameters setMinInlierRatio( final float minInlierRatio ) { this.minInlierRatio = minInlierRatio; return this;  }
	public RANSACParameters setMinInlierFactor( final float minInlierFactor ) { this.minInlierFactor = minInlierFactor; return this;  }
	public RANSACParameters setNumIterations( final int numIterations ) { this.numIterations = numIterations; return this;  }
}
