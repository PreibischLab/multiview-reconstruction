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
package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Defines a pair of corresponding interest points
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class CorrespondingInterestPoints implements Comparable< CorrespondingInterestPoints >
{
	/**
	 * The detection id of the interest point in this {@link InterestPointList}
	 */
	final int detectionId;
	
	/**
	 * The {@link ViewId} the corresponding interest point belongs to
	 */
	final ViewId correspondingViewId;
	
	/**
	 * The label of {@link InterestPointList} as stored in the {@link ViewInterestPointLists} HashMap
	 */
	final String correspondingLabel;
	
	/**
	 * The detection id of the corresponding interest point in the {@link InterestPointList}
	 */
	final int correspondingDetectionId;

	public CorrespondingInterestPoints( final CorrespondingInterestPoints c )
	{
		this( c.detectionId, c.correspondingViewId, c.correspondingLabel, c.correspondingDetectionId );
	}

	public CorrespondingInterestPoints( final int detectionId, final ViewId correspondingViewId, final String correspondingLabel, final int correspondingDetectionId )
	{
		this.detectionId = detectionId;
		this.correspondingViewId = correspondingViewId;
		this.correspondingLabel = correspondingLabel;
		this.correspondingDetectionId = correspondingDetectionId;
	}

	/**
	 * @return The detection id of the interest point in this {@link InterestPointList}
	 */
	final public int getDetectionId() { return detectionId; }

	/**
	 * @return The {@link ViewId} the corresponding interest point belongs to
	 */
	final public ViewId getCorrespondingViewId() { return correspondingViewId; }
	
	/**
	 * @return The label of {@link InterestPointList} as stored in the {@link ViewInterestPointLists} HashMap
	 */
	final public String getCorrespodingLabel() { return correspondingLabel; }
	
	/**
	 * @return The detection id of the corresponding interest point in the {@link InterestPointList}
	 */
	final public int getCorrespondingDetectionId() { return correspondingDetectionId; }

//	/**
//	 * Order by {@link #getCorrespondingViewId().getTimePointId()  timepoint} id, then
//	 * {@link #getCorrespondingViewId().getViewSetupId() setup} id, then detection id.
//	 */
	@Override
	public int compareTo( final CorrespondingInterestPoints o )
	{
		if ( getCorrespondingViewId().getTimePointId() == o.getCorrespondingViewId().getTimePointId() )
		{
			if ( getCorrespondingViewId().getViewSetupId() == o.getCorrespondingViewId().getViewSetupId() )
			{
				return getCorrespondingDetectionId() - o.getCorrespondingDetectionId();
			}
			else
			{
				return getCorrespondingViewId().getViewSetupId() - o.getCorrespondingViewId().getViewSetupId();
			}
		}
		else
		{
			return getCorrespondingViewId().getTimePointId() - o.getCorrespondingViewId().getTimePointId();
		}
	}
}
