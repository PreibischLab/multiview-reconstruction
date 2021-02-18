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
package net.preibisch.mvrecon.process.export;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public interface ImgExport
{
	/**
	 * Called last when the fusion is finished (e.g. to write the XML)
	 *
	 * @return - true if the spimdata was modified, otherwise false
	 */
	public boolean finish();

	/**
	 * Exports the image (min and max intensity will be computed)
	 * 
	 * @param img - Note, in rare cases this can be null (i.e. do nothing)
	 * @param bb - the bounding box used to fuse this image
	 * @param downsampling - how much it was downsampled (or NaN if not)
	 * @param anisoF - how much the z-dimension was scaled (or NaN if not)
	 * @param title - the name of the image
	 * @param fusionGroup - which views are part of this fusion
	 * @param <T> pixel type
	 * @return success? true or false
	 */
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup );
	
	/**
	 * Exports the image using a predefined min/max
	 * 
	 * @param img - Note, in rare cases this can be null (i.e. do nothing)
	 * @param bb - the bounding box used to fuse this image
	 * @param downsampling - how much it was downsampled (or NaN if not)
	 * @param anisoF - how much the z-dimension was scaled (or NaN if not)
	 * @param title - the name of the image
	 * @param fusionGroup - which views are part of this fusion
	 * @param min - define min intensity of this image
	 * @param max - define max intensity of this image
	 * @param <T> pixel type
	 * @return success? true or false
	 */
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final double anisoF,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final double min,
			final double max );
	
	/*
	 * Query the necessary parameters for the fusion (new dialog can be made)
	 * 
	 * @return success? true or false
	 */
	public abstract boolean queryParameters( final FusionExportInterface fusion );


	public abstract ImgExport newInstance();
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
}
