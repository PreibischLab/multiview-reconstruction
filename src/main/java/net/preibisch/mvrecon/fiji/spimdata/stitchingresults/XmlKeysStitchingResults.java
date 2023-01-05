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
package net.preibisch.mvrecon.fiji.spimdata.stitchingresults;

public class XmlKeysStitchingResults
{
	public static final String STITCHINGRESULTS_TAG = "StitchingResults";
	public static final String STITCHINGRESULT_PW_TAG = "PairwiseResult";

	public static final String STITCHING_VS_A_TAG = "view_setup_a";
	public static final String STITCHING_VS_B_TAG = "view_setup_b";
	public static final String STITCHING_TP_A_TAG = "tp_a";
	public static final String STITCHING_TP_B_TAG = "tp_b";
	public static final String STICHING_SHIFT_TAG = "shift";
	public static final String STICHING_CORRELATION_TAG = "correlation";
	public static final String STITCHING_HASH_TAG = "hash";
	public static final String STICHING_BBOX_TAG = "overlap_boundingbox";
	
}
