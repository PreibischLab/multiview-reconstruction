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
package net.preibisch.mvrecon.fiji.spimdata.intensityadjust;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.process.fusion.intensity.IntensityCorrection;

public class IntensityAdjustments
{
	public static final String baseN5 = "intensity_adjustments.n5";

	final URI baseDir;
	final String dataset;
	final String parameters;

	// the boolean stores if it was changed
	final private HashMap< ViewId, Pair< Coefficients, Boolean > > coefficientsMap;

	public IntensityAdjustments( final URI baseDir, final String dataset, final String parameters )
	{
		this.baseDir = baseDir;
		this.dataset = dataset;
		this.parameters = parameters;
		this.coefficientsMap = new HashMap<>();
	}

	public void setCoefficient( final ViewId viewId, final Coefficients coefficients )
	{
		coefficientsMap.put( viewId, new ValuePair<>( coefficients, true ) );
	}

	public void setCoefficients( final ViewId viewId, final Map< ViewId, Coefficients > coefficients )
	{
		coefficients.forEach( (v,c) -> coefficientsMap.put( v, new ValuePair<>( c, true ) ) );
	}

	public boolean saveInterestPoints( final boolean forceWrite )
	{
		boolean modified = coefficientsMap.values().stream().anyMatch( p -> p.getB() );

		if ( !modified && !forceWrite )
			return true;

		IntensityCorrection.writeCoefficients(null, baseN5, dataset, null);
		return false;
	}

	// TODO: this should become part of SpimData2 I'd say? Ultimately this is a property of the dataset that should also be displayed and potentially used during reconstruction
	public static void writeCoefficients(
			final N5Writer n5Writer,
			final String group,
			final String dataset,
			final Map<ViewId, Coefficients> coefficients
	) {
		coefficients.forEach((viewId, tile) -> {
			final int setupId = viewId.getViewSetupId();
			final int timePointId = viewId.getTimePointId();
			final String path = getCoefficientsDatasetPath(group, dataset, setupId, timePointId);
			CoefficientsIO.save(tile, n5Writer, path);
		});
	}

	public static Coefficients readCoefficients(
			final N5Reader n5Reader,
			final String group,
			final String dataset,
			final ViewId viewId
	) {
		final String path = getCoefficientsDatasetPath(group, dataset, viewId.getViewSetupId(), viewId.getTimePointId());
		return CoefficientsIO.load(n5Reader, path);
	}

	/**
	 * Get N5 path to coefficients for the specified view, as {@code "{group}/setup{setupId}/timepoint{timepointId}/{dataset}"}.
	 */
	static String getCoefficientsDatasetPath(
			final String group,
			final String dataset,
			final int setupId,
			final int timePointId
	) {
		return String.format("%s/setup%d/timepoint%d/%s", group, setupId, timePointId, dataset);
	}
}
