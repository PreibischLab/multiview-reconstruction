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
package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import mpicbg.spim.data.SpimDataException;

public class ExampleDirectLoading
{
	public static void main( String[] args ) throws SpimDataException
	{
		int viewSetupId = 1;

		//
		// works without the XML, just loads the N5 directly
		//
		InterestPointsN5 ip = new InterestPointsN5(
				URI.create("/nrs/saalfeld/john/for/keller/danio_1_488/dataset-orig-tifs/3/"),
				"tpId_0_viewSetupId_" + viewSetupId  + "/beads8v2" );

		List<InterestPoint> points = ip.getInterestPointsCopy();

		for ( final InterestPoint p : points )
		{
			System.out.println( p.getId() + " " + Arrays.toString( p.getL() ));
		}
	}
}
