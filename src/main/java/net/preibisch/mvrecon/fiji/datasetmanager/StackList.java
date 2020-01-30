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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.StackImgLoader;

public abstract class StackList 
{
	final public static char TIMEPOINT_PATTERN = 't';
	final public static char CHANNEL_PATTERN = 'c';
	final public static char ILLUMINATION_PATTERN = 'i';
	final public static char ANGLE_PATTERN = 'a';
	final public static char TILE_PATTERN = 'x';
	
	protected abstract int getDefaultMultipleAngles();
	protected abstract int getDefaultMultipleTimepoints();
	protected abstract int getDefaultMultipleChannels();
	protected abstract int getDefaultMultipleIlluminations();
	protected abstract int getDefaultMultipleTiles();
	
	protected abstract void setDefaultMultipleAngles( int defaultAngleChoice );
	protected abstract void setDefaultMultipleTimepoints( int defaultTimepointChoice );
	protected abstract void setDefaultMultipleChannels( int defaultChannelChoice );
	protected abstract void setDefaultMultipleIlluminations( int defaultIlluminationChoice );
	protected abstract void setDefaultMultipleTiles( int defaultTileChoice );
		
	protected int hasMultipleAngles, hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations, hasMultipleTiles;
	
	public static boolean showDebugFileNames = true;
	
	public static String defaultTimepoints = "18,19,30";
	public static String defaultChannels = "1,2";
	public static String defaultIlluminations = "0,1";
	public static String defaultAngles = "0-315:45";
	public static String defaultTiles = "1,2";

	protected String timepoints, channels, illuminations, angles, tiles;
	protected ArrayList< String > timepointNameList, channelNameList, illuminationsNameList, angleNameList, tileNameList;
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles, replaceTiles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles, numDigitsTiles;
	
	/* new int[]{ t, c, i, a } - indices */
	protected ArrayList< int[] > exceptionIds;
	

	public static int defaultContainer = 0;
	public ImgFactory< ? extends NativeType< ? > > imgFactory;
	public static int defaultCalibration1 = 0;
	public static int defaultCalibration2 = 1;
	public int calibration1, calibration2;
	
	protected HashMap< ViewSetupPrecursor, Calibration > calibrations = new HashMap< ViewSetupPrecursor, Calibration >();
	protected HashMap< ViewSetupPrecursor, double[]> locations = new HashMap<>();
	
	public static String defaultDirectory = "";
	public static String defaultFileNamePattern = null;

	protected String directory, fileNamePattern;
	
	protected abstract boolean supportsMultipleTimepointsPerFile();
	protected abstract boolean supportsMultipleChannelsPerFile();
	protected abstract boolean supportsMultipleAnglesPerFile();
	protected abstract boolean supportsMultipleIlluminationsPerFile();
	protected abstract boolean supportsMultipleTilesPerFile();
	
	protected abstract boolean canLoadTileLocationFromMeta();
	protected abstract double[] loadTileLocationFromMetaData(File file, int seriesOffset);
	
	protected boolean loadAllTileLocations()
	{
		
		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						for ( int ti = 0; ti < tileNameList.size(); ++ti )
						{
							
							// multiple tiles per file --> read series corresponding to tile else: first series
							final int seriesOffset = hasMultipleTiles == 2 ? ti : 0;
							
							// FIXME: see above
							if ( exceptionIds.size() > 0 && 
								 exceptionIds.get( 0 )[ 0 ] == t && exceptionIds.get( 0 )[ 1 ] == c && 
								 exceptionIds.get( 0 )[ 2 ] == t && exceptionIds.get( 0 )[ 3 ] == a )
							{
								continue;
							}
							else
							{
								final ViewSetupPrecursor vsp = new ViewSetupPrecursor( c, i, a, ti );
								
								if ( locations.get( vsp ) == null )
								{
									final double[] loc = loadTileLocationFromMetaData( new File( directory, getFileNameFor( t, c, i, a, ti ) ), seriesOffset );
									
									if ( loc != null )
										locations.put( vsp, loc );
								}
							}
						}
		
		return true;
	}
	
	protected class Calibration
	{
		public double calX = 1, calY = 1, calZ = 1;
		public String calUnit = "um";

		public Calibration( final double calX, final double calY, final double calZ, final String calUnit )
		{
			this.calX = calX;
			this.calY = calY;
			this.calZ = calZ;
			this.calUnit = calUnit;
		}
		
		public Calibration( final double calX, final double calY, final double calZ )
		{
			this.calX = calX;
			this.calY = calY;
			this.calZ = calZ;
		}
		
		public Calibration() {};
	}
	
	protected class ViewSetupPrecursor
	{
		final public int c, i, a, t;
		
		public ViewSetupPrecursor( final int c, final int i, final int a, final int t )
		{
			this.c = c;
			this.i = i;
			this.a = a;
			this.t = t;
		}
		
		@Override
		public int hashCode()
		{
			return t * tileNameList.size() * illuminationsNameList.size() * angleNameList.size() +
					c * illuminationsNameList.size() * angleNameList.size() + i * angleNameList.size() + a;
		}
		
		@Override
		public boolean equals( final Object o )
		{
			if ( o instanceof ViewSetupPrecursor )
				return c == ((ViewSetupPrecursor)o).c && i == ((ViewSetupPrecursor)o).i && a == ((ViewSetupPrecursor)o).a && t == ((ViewSetupPrecursor)o).t;
			else
				return false;
		}
		
		@Override
		public String toString() 
		{
			return "channel=" + channelNameList.get( c ) + ", ill.dir.=" + illuminationsNameList.get( i ) + 
					", angle=" + angleNameList.get( a ) + ", tile=" + tileNameList.get( t ); 
		}
	}
	
	

	/*
	 * Instantiate the {@link ImgLoader}
	 * 
	 * @param path - The path relative to the basepath
	 * @param basePath - The base path, where XML will be and the image stack are
	 * @param sequenceDescription 
	 * @return StackImgLoader
	 */
	protected abstract StackImgLoader createAndInitImgLoader( final String path, final File basePath, final ImgFactory< ? extends NativeType< ? > > imgFactory, SequenceDescription sequenceDescription );

	/*
	 * Assembles the list of missing view instances, i.e. {@link ViewSetup} that
	 * are missing at certain {@link TimePoint}s.
	 * 
	 * @return
	 */
	protected MissingViews createMissingViews()
	{
		// TODO + FIXME: handle tiles here!
		if ( exceptionIds.size() == 0 )
			return null;

		final ArrayList< ViewId > missingViews = new ArrayList< ViewId >();
				
		for ( int t = 0; t < timepointNameList.size(); ++t )
		{			
			// assemble a subset of exceptions for the current timepoint
			final ArrayList< int[] > tmp = new ArrayList< int[] >();
			
			for ( int[] exceptions : exceptionIds )
				if ( exceptions[ 0 ] == t )
					tmp.add( exceptions );
		
			if ( tmp.size() > 0 )
			{
				int setupId = 0;

				for ( int c = 0; c < channelNameList.size(); ++c )
					for ( int i = 0; i < illuminationsNameList.size(); ++i )
						for ( int a = 0; a < angleNameList.size(); ++a )
						{
							for ( int[] exceptions : tmp )
								if ( exceptions[ 1 ] == c && exceptions[ 2 ] == i && exceptions[ 3 ] == a )
								{
									missingViews.add( new ViewId( Integer.parseInt( timepointNameList.get( t ) ), setupId ) );
									System.out.println( "creating missing views t:" + Integer.parseInt( timepointNameList.get( t ) ) + " c:" + c + " i:" + i + " a:" + a + " setupid: " + setupId );
								}
							
							++setupId;
						}
			}
		}
		
		return new MissingViews( missingViews );
	}

	/*
	 * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
	 * The {@link ViewSetup} are defined independent of the {@link TimePoint},
	 * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
	 * class defines if some of them are missing for some of the {@link TimePoint}s
	 *
	 * @return
	 */
	protected ArrayList< ViewSetup > createViewSetups()
	{
		final ArrayList< Channel > channels = new ArrayList< Channel >();
		for ( int c = 0; c < channelNameList.size(); ++c )
			channels.add( new Channel( c, channelNameList.get( c ) ) );

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		for ( int i = 0; i < illuminationsNameList.size(); ++i )
			illuminations.add( new Illumination( i, illuminationsNameList.get( i ) ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		for ( int a = 0; a < angleNameList.size(); ++a )
			angles.add( new Angle( a, angleNameList.get( a ) ) );
		
		final ArrayList< Tile > tiles = new ArrayList<>();
		for ( int t = 0; t < tileNameList.size(); ++t )
			tiles.add( new Tile( t, tileNameList.get( t ) ) );

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channels )
			for ( final Illumination i : illuminations )
				for ( final Angle a : angles )
					for ( final Tile t : tiles)
					{						
						// set location if we can
						if (canLoadTileLocationFromMeta())
							t.setLocation( locations.get( new ViewSetupPrecursor( c.getId(), i.getId(), a.getId(), t.getId() ) ) );
						
						final Calibration cal = calibrations.get( new ViewSetupPrecursor( c.getId(), i.getId(), a.getId(), t.getId() ) );
						final VoxelDimensions voxelSize = new FinalVoxelDimensions( cal.calUnit, cal.calX, cal.calY, cal.calZ );
						// TODO: Dimensions should not be null
						viewSetups.add( new ViewSetup( viewSetups.size(), Integer.toString( viewSetups.size() ), null, voxelSize, t, c, a, i ) );
					}

		return viewSetups;
	}

	/*
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 */
	protected TimePoints createTimePoints()
	{
		try
		{
			return new TimePointsPattern( timepoints );
		}
		catch ( final ParseException e )
		{
			throw new RuntimeException( e );
		}
	}

	public static ArrayList< String > convertIntegerList( final List< Integer > list )
	{
		final ArrayList< String > stringList = new ArrayList< String >();
		
		for ( final int i : list )
			stringList.add( Integer.toString( i ) );
		
		return stringList;
	}
	
	/*
	 * Assemble the filename for the corresponding file based on the indices for time, channel, illumination, angle and tile
	 * 
	 * If the fileNamePattern is separated by ';', it will return multiple solutions for each filenamepattern
	 * 
	 * @param tpID
	 * @param chID
	 * @param illID
	 * @param angleID
	 * @param tileID
	 * @return
	 */
	protected String getFileNameFor( final int tpID, final int chID, final int illID, final int angleID, final int tileID )
	{
		return getFileNamesFor( fileNamePattern, replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles, replaceTiles,
				timepointNameList.get( tpID ), channelNameList.get( chID ), illuminationsNameList.get( illID ), angleNameList.get( angleID ), tileNameList.get( tileID ),
				numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles, numDigitsTiles )[ 0 ];
	}

	/*
	 * Assemble the filename for the corresponding file based on the indices for time, channel, illumination and angle and tile
	 * 
	 * If the fileNamePattern is separated by ';', it will return multiple solutions for each filenamepattern
	 * 
	 * @param fileNames
	 * @param replaceTimepoints
	 * @param replaceChannels
	 * @param replaceIlluminations
	 * @param replaceAngles
	 * @param replaceTiles
	 * @param tpName
	 * @param chName
	 * @param illName
	 * @param angleName
	 * @param tileName
	 * @param numDigitsTP
	 * @param numDigitsCh
	 * @param numDigitsIll
	 * @param numDigitsAngle
	 * @param numDigitsTile
	 * @return
	 */
	public static String[] getFileNamesFor( String fileNames, 
			final String replaceTimepoints, final String replaceChannels, 
			final String replaceIlluminations, final String replaceAngles, final String replaceTiles,
			final String tpName, final String chName, final String illName, final String angleName, final String tileName,
			final int numDigitsTP, final int numDigitsCh, final int numDigitsIll, final int numDigitsAngle, final int numDigitsTile )
	{
		String[] fileName = fileNames.split( ";" );
		
		for ( int i = 0; i < fileName.length; ++i )
		{
			if ( replaceTimepoints != null )
				fileName[ i ] = fileName[ i ].replace( replaceTimepoints, leadingZeros( tpName, numDigitsTP ) );
	
			if ( replaceChannels != null )
				fileName[ i ] = fileName[ i ].replace( replaceChannels, leadingZeros( chName, numDigitsCh ) );
	
			if ( replaceIlluminations != null )
				fileName[ i ] = fileName[ i ].replace( replaceIlluminations, leadingZeros( illName, numDigitsIll ) );
	
			if ( replaceAngles != null )
				fileName[ i ] = fileName[ i ].replace( replaceAngles, leadingZeros( angleName, numDigitsAngle ) );
			
			if ( replaceTiles != null )
				fileName[ i ] = fileName[ i ].replace( replaceTiles, leadingZeros( tileName, numDigitsTile ) );
		}
		return fileName;
	}
	
	public static String leadingZeros( String s, final int numDigits )
	{
		while ( s.length() < numDigits )
			s = "0" + s;
		
		return s;
	}

	/*
	 * populates the fields calX, calY, calZ from the first file of the series
	 * 
	 * @return - true if successful
	 */
	protected boolean loadFirstCalibration()
	{
		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						for ( int ti = 0; ti < tileNameList.size(); ++ti )
						{						
							// FIXME: tiles in Exceptions
							if ( exceptionIds.size() > 0 && 
								 exceptionIds.get( 0 )[ 0 ] == t && exceptionIds.get( 0 )[ 1 ] == c && 
								 exceptionIds.get( 0 )[ 2 ] == t && exceptionIds.get( 0 )[ 3 ] == a )
							{
								continue;
							}
							else
							{
								final Calibration cal = loadCalibration( new File( directory, getFileNameFor( t, c, i, a, ti ) ) );
								
								if ( cal == null )
								{
									return false;
								}
								else
								{
									calibrations.put( new ViewSetupPrecursor( c, i, a, ti ), cal );
									return true;
								}
							}
						}
		
		return false;
	}

	/*
	 * populates the fields calX, calY, calZ from the first file of the series
	 * 
	 * @return - true if successful
	 */
	protected boolean loadAllCalibrations()
	{
		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						for ( int ti = 0; ti < tileNameList.size(); ++ti )
						{
							// FIXME: see above
							if ( exceptionIds.size() > 0 && 
								 exceptionIds.get( 0 )[ 0 ] == t && exceptionIds.get( 0 )[ 1 ] == c && 
								 exceptionIds.get( 0 )[ 2 ] == t && exceptionIds.get( 0 )[ 3 ] == a )
							{
								continue;
							}
							else
							{
								final ViewSetupPrecursor vsp = new ViewSetupPrecursor( c, i, a, ti );
								
								if ( calibrations.get( vsp ) == null )
								{
									final Calibration cal = loadCalibration( new File( directory, getFileNameFor( t, c, i, a, ti ) ) );
									
									if ( cal != null )
										calibrations.put( vsp, cal );
								}
							}
						}
		
		return true;
	}

	/*
	 * Loads the calibration stored in a specific file and closes it afterwards. Depends on the type of opener that is used.
	 * 
	 * @param file
	 * @return - the Calibration or null if it could not be loaded for some reason
	 */
	protected abstract Calibration loadCalibration( final File file );

	protected String assembleDefaultPattern()
	{
		String pattern = "spim";
		
		if ( hasMultipleTimePoints == 1 )
			pattern += "_TL{t}";
		
		if ( hasMultipleChannels == 1 )
			pattern += "_Channel{c}";
		
		if ( hasMultipleIlluminations == 1 )
			pattern += "_Illum{i}";
		
		if ( hasMultipleAngles == 1 )
			pattern += "_Angle{a}";
		
		if ( hasMultipleTiles == 1 )
			pattern += "_Tile{x}";
		
		return pattern + ".tif";
	}
	
		
}
