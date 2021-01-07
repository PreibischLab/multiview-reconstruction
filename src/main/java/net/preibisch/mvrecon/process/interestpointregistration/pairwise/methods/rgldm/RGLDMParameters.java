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
