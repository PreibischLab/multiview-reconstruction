/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2023 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range.TimepointRange;

public class IndividualTimepoints extends AllToAllRange< ViewId, TimepointRange< ViewId > >
{
	public IndividualTimepoints(
			final List< ViewId > views,
			final Set< Group< ViewId > > groups )
	{
		super( views, groups, new TimepointRange< ViewId >( 0 ) );
	}

	public static < V extends ViewId > List< Pair< V, V > > allPairs(
			final List< ? extends V > views,
			final Collection< ? extends Group< V > > groups )
	{
		return AllToAllRange.allPairs( views, groups, new TimepointRange< V >( 0 ) );
	}
}
