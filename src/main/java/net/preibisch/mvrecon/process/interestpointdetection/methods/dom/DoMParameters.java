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
package net.preibisch.mvrecon.process.interestpointdetection.methods.dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointdetection.methods.InterestPointParameters;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;

/**
 * Created by schmied on 01/07/15.
 */
public class DoMParameters extends InterestPointParameters
{
	/**
	 * 0 = no subpixel localization
	 * 1 = quadratic fit
	 */
	public int localization = 1;

	public int radius1 = 2;
	public int radius2 = 3;
	public float threshold = (float) 0.005;
	public boolean findMin = false;
	public boolean findMax = true;
}
