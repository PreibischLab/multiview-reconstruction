/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.util.Collection;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;

public interface FusionExportInterface
{
	SpimData getSpimData();

	/**
	 * 0 == "32-bit floating point",
	 * 1 == "16-bit unsigned integer"
	 *
	 * @return the pixel type
	 */
	int getPixelType();

	/**
	 * 0 == "Each timepoint &amp; channel",
	 * 1 == "Each timepoint, channel &amp; illumination",
	 * 2 == "All views together",
	 * 3 == "Each view"
	 * 
	 * @return - how the views that are processed are split up
	 */
	int getSplittingType();

	Interval getDownsampledBoundingBox();
	double getDownsampling();
	Collection< ? extends ViewId > getViews();
}
