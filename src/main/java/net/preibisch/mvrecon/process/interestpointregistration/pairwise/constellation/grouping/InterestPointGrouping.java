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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Interface for grouping interest points from multiple views that are in one group
 * 
 * @author spreibi
 */
public abstract class InterestPointGrouping< V extends ViewId > implements Grouping< V, List< GroupedInterestPoint< V > > >
{
	// all interestpoints
	final Map< V, List< InterestPoint > > interestpoints;
	int before, after;

	public InterestPointGrouping( final Map< V, List< InterestPoint > > interestpoints )
	{
		this.interestpoints = interestpoints;
	}

	public int countBefore() { return before; }
	public int countAfter() { return after; }

	@Override
	public List< GroupedInterestPoint< V > > group( final Group< V > group )
	{
		final Map< V, List< InterestPoint > > toMerge = new HashMap<>();

		this.before = 0;

		for ( final V view : group )
		{
			final List< InterestPoint > points = interestpoints.get( view );

			if ( points == null )
				throw new RuntimeException( "no interestpoints available" );

			before += points.size();
	
			toMerge.put( view, points );
		}

		final List< GroupedInterestPoint< V > > merged = merge( toMerge );

		this.after = merged.size();

		return merged;
	}

	protected abstract List< GroupedInterestPoint< V > > merge( Map< V, List< InterestPoint > > toMerge );
}
