/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
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
