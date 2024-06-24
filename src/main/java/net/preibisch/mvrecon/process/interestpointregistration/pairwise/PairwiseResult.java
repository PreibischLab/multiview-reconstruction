/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2024 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise;

import java.util.Date;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.mpicbg.PointMatchGeneric;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

public class PairwiseResult< I extends InterestPoint >
{
	private List< PointMatchGeneric< I > > candidates, inliers;
	private double error = Double.NaN;
	private long time = 0;
	private String result = "", desc = "";
	private String labelA, labelB;

	boolean printout = false, storeCorrespondences = true;

	public PairwiseResult( final boolean storeCorrespondences )
	{
		this.storeCorrespondences = storeCorrespondences;
		this.printout = true;
	}

	public void setLabelA( final String labelA ) { this.labelA = labelA; }
	public void setLabelB( final String labelB ) { this.labelB = labelB; }

	public String getLabelA() { return labelA; }
	public String getLabelB() { return labelB; }

	public boolean storeCorrespondences() { return storeCorrespondences; }
	public void setPrintOut( final boolean printOut ) { this.printout = printOut; }
	public void setResult( final long time, final String result )
	{
		this.time = time;
		this.result = result;
		if ( printout && desc.length() > 0 ) IOFunctions.println( getFullDesc() );
	}
	public void setDescriptions( final String desc ) { this.desc = desc; }
	public List< PointMatchGeneric< I > > getCandidates() { return candidates; }
	public List< PointMatchGeneric< I > > getInliers() { return inliers; }
	public String getDescription() { return desc; }
	public void setDescription( final String desc )
	{
		this.desc = desc;
		if ( printout && result.length() > 0 ) IOFunctions.println( getFullDesc() );
	}
	public double getError() { return error; }
	public void setCandidates( final List< PointMatchGeneric< I > > candidates ) { this.candidates = candidates; }
	public void setInliers( final List< PointMatchGeneric< I > > inliers, final double error )
	{
		this.inliers = inliers;
		this.error = error;
	}

	public String getFullDesc() { return "(" + new Date( time ) + "): " + desc + ": " + result; }
}
