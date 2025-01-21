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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.global;

import java.util.ArrayList;
import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;

public class IndividualTimepointGUI implements GlobalGUI
{
	final SpimData2 data;

	public IndividualTimepointGUI( final SpimData2 data )
	{
		this.data = data;
	}

	public List< List< ViewId > > getIndividualSets( final List< ViewId > viewIds )
	{
		final ArrayList< List< ViewId > > sets = new ArrayList<>();

		for ( final TimePoint timepoint : SpimData2.getAllTimePointsSorted( data, viewIds ) )
		{
			final ArrayList< ViewId > set = new ArrayList<>();

			for ( final ViewId viewId : viewIds )
				if ( viewId.getTimePointId() == timepoint.getId() )
					set.add( viewId );

			sets.add( set );
		}

		return sets;
	}
}
