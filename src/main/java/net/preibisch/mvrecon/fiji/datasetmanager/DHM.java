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
package net.preibisch.mvrecon.fiji.datasetmanager;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;

public class DHM 
{
	public static String defaultDir = "";
	public static double defaulCalX = 0.1725; //3.45 / 20.0;
	public static double defaulCalY = 0.1725; //3.45 / 20.0;
	public static double defaulCalZ = 1.25; //( 0.5 / ( 20.0 * 20.0 ) ) * 1000;
	public static String defaulCalUnit = "um";
	public static boolean defaultOpenAll = false;
	
	/*
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 */
	protected TimePoints createTimePoints( final DHMMetaData meta )
	{
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();

		for ( int t = 0; t < meta.getTimepoints().size(); ++t )
			timepoints.add( new TimePoint( Integer.parseInt( meta.getTimepoints().get( t ) ) ) );

		return new TimePoints( timepoints );
	}

	/*
	 * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
	 * The {@link ViewSetup} are defined independent of the {@link TimePoint},
	 * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
	 * class defines if some of them are missing for some of the {@link TimePoint}s
	 *
	 * @return
	 */
	protected ArrayList< ViewSetup > createViewSetups( final DHMMetaData meta )
	{
		final ArrayList< Channel > channels = new ArrayList< Channel >();
		channels.add( new Channel( meta.getAmpChannelId(), meta.getAmplitudeDir() ) );
		channels.add( new Channel( meta.getPhaseChannelId(), meta.getPhaseDir() ) );

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		illuminations.add( new Illumination( 0, String.valueOf( 0 ) ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		angles.add( new Angle( 0, String.valueOf( 0 ) ) );

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channels )
			for ( final Illumination i : illuminations )
				for ( final Angle a : angles )
				{
					final VoxelDimensions voxelSize = new FinalVoxelDimensions( meta.calUnit, meta.calX, meta.calY, meta.calZ );
					final Dimensions dim = new FinalDimensions( new long[]{ meta.getWidth(), meta.getHeight(), meta.getDepth() } );
					viewSetups.add( new ViewSetup( viewSetups.size(), null, dim, voxelSize, c, a, i ) );
				}

		return viewSetups;
	}
}
