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

import java.io.File;

import mpicbg.spim.data.sequence.SequenceDescription;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LegacyStackImgLoaderLOCI;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.StackImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.StackImgLoaderLOCI;

public class StackListLOCI extends StackList
{
	public static int defaultAngleChoice = 1;
	public static int defaultTimePointChoice = 1;
	public static int defaultChannelleChoice = 0;
	public static int defaultIlluminationChoice = 0;
	public static int defaultTileChoice = 0;
	
	@Override
	protected StackImgLoader< ? > createAndInitImgLoader( final String path, final File basePath, final ImgFactory< ? extends NativeType< ? > > imgFactory, SequenceDescription sequenceDescription )
	{
		return new StackImgLoaderLOCI(
				new File( basePath.getAbsolutePath(), path ),
				fileNamePattern, imgFactory,
				hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations, hasMultipleAngles, hasMultipleTiles,
				sequenceDescription );
	}


	@Override
	protected double[] loadTileLocationFromMetaData(File file, int seriesOffset) {
		IOFunctions.println( "Loading tile localtion for: " + file.getAbsolutePath() );
		
		if ( !file.exists() )
		{
			IOFunctions.println( "File '" + file + "' does not exist. Stopping." );
			return null;
		}

		final double[] loc = LegacyStackImgLoaderLOCI.loadTileLocation( file, seriesOffset );
		return loc;
	};
	
	@Override
	protected Calibration loadCalibration( final File file )
	{
		IOFunctions.println( "Loading calibration for: " + file.getAbsolutePath() );
				
		if ( !file.exists() )
		{
			IOFunctions.println( "File '" + file + "' does not exist. Stopping." );
			return null;
		}

		final net.preibisch.mvrecon.fiji.spimdata.imgloaders.Calibration cal = LegacyStackImgLoaderLOCI.loadMetaData( file );

		if ( cal == null )
			return null;
		
		final double calX = cal.getCalX();
		final double calY = cal.getCalY();
		final double calZ = cal.getCalZ();

		return new Calibration( calX, calY, calZ );
	}

	@Override
	protected boolean canLoadTileLocationFromMeta() {return true;};
	
	@Override
	protected boolean supportsMultipleTimepointsPerFile() { return true; }

	@Override
	protected boolean supportsMultipleChannelsPerFile() { return true; }

	@Override
	protected boolean supportsMultipleAnglesPerFile() { return false; }

	@Override
	protected boolean supportsMultipleIlluminationsPerFile() { return false; }
	
	@Override
	protected boolean supportsMultipleTilesPerFile() { return true; }
	
	@Override
	protected int getDefaultMultipleAngles() { return defaultAngleChoice; }

	@Override
	protected int getDefaultMultipleTimepoints() { return defaultTimePointChoice; }

	@Override
	protected int getDefaultMultipleChannels() { return defaultChannelleChoice; }

	@Override
	protected int getDefaultMultipleIlluminations() { return defaultIlluminationChoice; }
	
	@Override
	protected int getDefaultMultipleTiles() { return defaultTileChoice; }

	@Override
	protected void setDefaultMultipleAngles( final int a ) { defaultAngleChoice = a; }

	@Override
	protected void setDefaultMultipleTimepoints( final int t ) { defaultTimePointChoice = t; }

	@Override
	protected void setDefaultMultipleChannels( final int c ) { defaultChannelleChoice = c; }

	@Override
	protected void setDefaultMultipleIlluminations( final int i ) { defaultIlluminationChoice = i; }
	
	@Override
	protected void setDefaultMultipleTiles( final int ti ) { defaultTileChoice = ti; }


}
