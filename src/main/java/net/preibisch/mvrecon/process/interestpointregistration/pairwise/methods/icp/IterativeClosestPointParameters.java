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
