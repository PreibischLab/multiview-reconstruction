/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;

public class ReferenceTimepointRange< V extends ViewId > implements RangeComparator< V >
{
	final int referenceTimepoint;

	public ReferenceTimepointRange( final int referenceTimepoint )
	{
		this.referenceTimepoint = referenceTimepoint;
	}
	public ReferenceTimepointRange( final TimePoint referenceTimepoint )
	{
		this( referenceTimepoint.getId() );
	}

	@Override
	public boolean inRange( final V view1, final V view2 )
	{
		// if one of the views is a reference timepoint or if they are from the same timepoint (fixed views are discarded later)
		if ( view1.getTimePointId() == referenceTimepoint || view2.getTimePointId() == referenceTimepoint || view1.getTimePointId() == view2.getTimePointId() )
			return true;
		else
			return false;
	}

	public int getReferenceTimepointId() { return referenceTimepoint; }
}
