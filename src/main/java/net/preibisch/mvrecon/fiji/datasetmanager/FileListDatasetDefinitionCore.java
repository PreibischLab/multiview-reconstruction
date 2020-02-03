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

//import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

//import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.AngleInfo;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.ChannelInfo;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileInfo;
import net.preibisch.mvrecon.fiji.datasetmanager.patterndetector.FilenamePatternDetector;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.FileMapImgLoaderLOCI;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2.FileMapImgLoaderLOCI2;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

public class FileListDatasetDefinitionCore
{
	public static final String[] GLOB_SPECIAL_CHARS = new String[] {"{", "}", "[", "]", "*", "?"};
		
	public static List<File> getFilesFromPattern(String pattern, final long fileMinSize)
	{		
		Pair< String, String > pAndp = splitIntoPathAndPattern( pattern, GLOB_SPECIAL_CHARS );		
		String path = pAndp.getA();
		String justPattern = pAndp.getB();
		
		PathMatcher pm;
		try
		{
		pm = FileSystems.getDefault().getPathMatcher( "glob:" + 
				((justPattern.length() == 0) ? path : String.join("/", path, justPattern )) );
		}
		catch (PatternSyntaxException e) {
			// malformed pattern, return empty list (for now)
			// if we do not catch this, we will keep logging exceptions e.g. while user is typing something like [0-9]
			return new ArrayList<>();
		}
		
		List<File> paths = new ArrayList<>();
		
		if (!new File( path ).exists())
			return paths;
		
		int numLevels = justPattern.split( "/" ).length;
						
		try
		{
			Files.walk( Paths.get( path ), numLevels ).filter( p -> pm.matches( p ) ).filter( new Predicate< Path >()
			{

				@Override
				public boolean test(Path t)
				{
					// ignore directories
					if (Files.isDirectory( t ))
						return false;
					
					try
					{
						return Files.size( t ) > fileMinSize;
					}
					catch ( IOException e )
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return false;
				}
			} )
			.forEach( p -> paths.add( new File(p.toString() )) );

		}
		catch ( IOException e )
		{
			
		}
		
		Collections.sort( paths );
		return paths;
	}
	
	protected static SpimData2 buildSpimData( FileListViewDetectionState state, boolean withVirtualLoader )
	{
		
		//final Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > fm = tileIdxMap;
		//fm.forEach( (k,v ) -> {System.out.println( k ); v.forEach( p -> {System.out.print(p.getA() + ""); System.out.print(p.getB().getA().toString() + " "); System.out.println(p.getB().getB().toString());} );});
		
		
		Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > tpIdxMap = state.getIdMap().get( TimePoint.class );
		Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > channelIdxMap = state.getIdMap().get( Channel.class );
		Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > illumIdxMap = state.getIdMap().get( Illumination.class );
		Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > tileIdxMap = state.getIdMap().get( Tile.class );
		Map< Integer, List< Pair< File, Pair< Integer, Integer > > > > angleIdxMap = state.getIdMap().get( Angle.class );
		
		
		List<Integer> timepointIndexList = new ArrayList<>(tpIdxMap.keySet());
		List<Integer> channelIndexList = new ArrayList<>(channelIdxMap.keySet());
		List<Integer> illuminationIndexList = new ArrayList<>(illumIdxMap.keySet());
		List<Integer> tileIndexList = new ArrayList<>(tileIdxMap.keySet());
		List<Integer> angleIndexList = new ArrayList<>(angleIdxMap.keySet());
		
		Collections.sort( timepointIndexList );
		Collections.sort( channelIndexList );
		Collections.sort( illuminationIndexList );
		Collections.sort( tileIndexList );
		Collections.sort( angleIndexList );
		
		int nTimepoints = timepointIndexList.size();
		int nChannels = channelIndexList.size();
		int nIlluminations = illuminationIndexList.size();
		int nTiles = tileIndexList.size();
		int nAngles = angleIndexList.size();
		
		List<ViewSetup> viewSetups = new ArrayList<>();
		List<ViewId> missingViewIds = new ArrayList<>();
		List<TimePoint> timePoints = new ArrayList<>();

		HashMap<Pair<Integer, Integer>, Pair<File, Pair<Integer, Integer>>> ViewIDfileMap = new HashMap<>();
		Integer viewSetupId = 0;
		for (Integer c = 0; c < nChannels; c++)
			for (Integer i = 0; i < nIlluminations; i++)
				for (Integer ti = 0; ti < nTiles; ti++)
					for (Integer a = 0; a < nAngles; a++)
					{
						// remember if we already added a vs in the tp loop
						boolean addedViewSetup = false;
						for (Integer tp = 0; tp < nTimepoints; tp++)
						{
														
							List< Pair< File, Pair< Integer, Integer > > > viewList;
							viewList = FileListDatasetDefinitionUtil.listIntersect( channelIdxMap.get( channelIndexList.get( c ) ), angleIdxMap.get( angleIndexList.get( a ) ) );
							viewList = FileListDatasetDefinitionUtil.listIntersect( viewList, tileIdxMap.get( tileIndexList.get( ti ) ) );
							viewList = FileListDatasetDefinitionUtil.listIntersect( viewList, illumIdxMap.get( illuminationIndexList.get( i ) ) );
							
							// we only consider combinations of angle, illum, channel, tile that are in at least one timepoint
							if (viewList.size() == 0)
								continue;
							
							viewList = FileListDatasetDefinitionUtil.listIntersect( viewList, tpIdxMap.get( timepointIndexList.get( tp ) ) );

														
							Integer tpId = timepointIndexList.get( tp );
							Integer channelId = channelIndexList.get( c );
							Integer illuminationId = illuminationIndexList.get( i );
							Integer angleId = angleIndexList.get( a );
							Integer tileId = tileIndexList.get( ti );
							
							System.out.println( "VS: " + viewSetupId );
							
							if (viewList.size() < 1)
							{
								System.out.println( "Missing View: ch" + c +" a"+ a + " ti" + ti + " tp"+ tp + " i" + i );
								int missingSetup = addedViewSetup ? viewSetupId - 1 : viewSetupId;
								missingViewIds.add( new ViewId( tpId, missingSetup ) );
								
							}
							else if (viewList.size() > 1)
								System.out.println( "Error: more than one View: ch" + c +" a"+ a + " ti" + ti + " tp"+ tp + " i" + i );
							else
							{
								System.out.println( "Found View: ch" + c +" a"+ a + " ti" + ti + " tp"+ tp + " i" + i + " in file " + viewList.get( 0 ).getA().getAbsolutePath());
								
								TimePoint tpI = new TimePoint( tpId );
								if (!timePoints.contains( tpI ))
									timePoints.add( tpI );
								
								if (!addedViewSetup)
									ViewIDfileMap.put( new ValuePair< Integer, Integer >( tpId, viewSetupId ), viewList.get( 0 ) );
								else
									ViewIDfileMap.put( new ValuePair< Integer, Integer >( tpId, viewSetupId - 1 ), viewList.get( 0 ) );
								
								
								// we have not visited this combination before
								if (!addedViewSetup)
								{
									Illumination illumI = new Illumination( illuminationId, illuminationId.toString() );
									
									Channel chI = new Channel( channelId, channelId.toString() );
									
									if (state.getDetailMap().get( Channel.class ) != null && state.getDetailMap().get( Channel.class ).containsKey( channelId))
									{
										ChannelInfo chInfoI = (ChannelInfo) state.getDetailMap().get( Channel.class ).get( channelId );
										if (chInfoI.wavelength != null)
											chI.setName( Integer.toString( (int)Math.round( chInfoI.wavelength )));
										if (chInfoI.fluorophore != null)
											chI.setName( chInfoI.fluorophore );
										if (chInfoI.name != null)
											chI.setName( chInfoI.name );
									}

									Angle aI = new Angle( angleId, angleId.toString() );
									
									if (state.getDetailMap().get( Angle.class ) != null && state.getDetailMap().get( Angle.class ).containsKey( angleId ))
									{
										AngleInfo aInfoI = (AngleInfo) state.getDetailMap().get( Angle.class ).get( angleId );
										
										if (aInfoI.angle != null && aInfoI.axis != null)
										{
											try
											{
												double[] axis = null;
												if ( aInfoI.axis == 0 )
													axis = new double[]{ 1, 0, 0 };
												else if ( aInfoI.axis == 1 )
													axis = new double[]{ 0, 1, 0 };
												else if ( aInfoI.axis == 2 )
													axis = new double[]{ 0, 0, 1 };

												if ( axis != null && !Double.isNaN( aInfoI.angle ) &&  !Double.isInfinite( aInfoI.angle ) )
													aI.setRotation( axis, aInfoI.angle );
											}
											catch ( Exception e ) {};
										}
									}

									Tile tI = new Tile( tileId, tileId.toString() );

									if (state.getDetailMap().get( Tile.class ) != null && state.getDetailMap().get( Tile.class ).containsKey( tileId ))
									{
										TileInfo tInfoI = (TileInfo) state.getDetailMap().get( Tile.class ).get( tileId );

										// check if we have at least one location != null
										// in the case that location in one dimension (e.g. z) is null, it is set to 0
										final boolean hasLocation = (tInfoI.locationX != null) || (tInfoI.locationY != null) || (tInfoI.locationZ != null);
										if (hasLocation)
											tI.setLocation( new double[] {
													tInfoI.locationX != null ? tInfoI.locationX : 0,
													tInfoI.locationY != null ? tInfoI.locationY : 0,
													tInfoI.locationZ != null ? tInfoI.locationZ : 0} );
									}

									ViewSetup vs = new ViewSetup( viewSetupId, 
													viewSetupId.toString(), 
													state.getDimensionMap().get( (viewList.get( 0 ))).getA(),
													state.getDimensionMap().get( (viewList.get( 0 ))).getB(),
													tI, chI, aI, illumI );

									viewSetups.add( vs );
									viewSetupId++;
									addedViewSetup = true;
								
								}
								
							}
						}
					}
		
		
		
		SequenceDescription sd = new SequenceDescription( new TimePoints( timePoints ), viewSetups , null, new MissingViews( missingViewIds ));
		
		HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap = new HashMap<>();
		for (Pair<Integer, Integer> k : ViewIDfileMap.keySet())
		{
			System.out.println( k.getA() + " " + k.getB() );
			ViewDescription vdI = sd.getViewDescription( k.getA(), k.getB() );
			System.out.println( vdI );
			if (vdI != null && vdI.isPresent()){
				fileMap.put( vdI, ViewIDfileMap.get( k ) );
			}
		}

		final ImgLoader imgLoader;
		if (withVirtualLoader)
			imgLoader = new FileMapImgLoaderLOCI2( fileMap, FileListDatasetDefinitionUtil.selectImgFactory(state.getDimensionMap()), sd, state.getWasZGrouped() );
		else
			imgLoader = new FileMapImgLoaderLOCI( fileMap, FileListDatasetDefinitionUtil.selectImgFactory(state.getDimensionMap()), sd, state.getWasZGrouped() );
		sd.setImgLoader( imgLoader );

		double minResolution = Double.MAX_VALUE;
		for ( VoxelDimensions d : state.getDimensionMap().values().stream().map( p -> p.getB() ).collect( Collectors.toList() ) )
		{
			for (int di = 0; di < d.numDimensions(); di++)
				minResolution = Math.min( minResolution, d.dimension( di ) );
		}

		// create calibration + translation view registrations
		ViewRegistrations vrs = DatasetCreationUtils.createViewRegistrations( sd.getViewDescriptions(), minResolution );

		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sd.getViewDescriptions() );

		SpimData2 data = new SpimData2( new File("/"), sd, vrs, viewInterestPoints, new BoundingBoxes(), new PointSpreadFunctions(), new StitchingResults(), new IntensityAdjustments() );
		return data;
	}


	
	public static File getLongestPathPrefix(Collection<String> paths)
	{
		String prefixPath = paths.stream().reduce( paths.iterator().next(), 
				(a,b) -> {
					List<String> aDirs = Arrays.asList( a.split(Pattern.quote(File.separator) ));
					List<String> bDirs = Arrays.asList( b.split( Pattern.quote(File.separator) ));
					List<String> res = new ArrayList<>();
					for (int i = 0; i< Math.min( aDirs.size(), bDirs.size() ); i++)
					{
						if (aDirs.get( i ).equals( bDirs.get( i ) ))
							res.add(aDirs.get( i ));
						else {
							break;
						}
					}
					return String.join(File.separator, res );
				});
		return new File(prefixPath);
		
	}

	
	public static boolean containsAny(String s, String ... templates)
	{
		for (int i = 0; i < templates.length; i++)
			if (s.contains( templates[i] ))
				return true;
		return false;
	}
	
	public static Pair<String, String> splitIntoPrefixAndPattern(FilenamePatternDetector detector)
	{
		final String stringRepresentation = detector.getStringRepresentation();
		final List< String > beforePattern = new ArrayList<>();
		final List< String > afterPattern = new ArrayList<>();
		
		boolean found = false;
		for (String s : Arrays.asList( stringRepresentation.split(Pattern.quote(File.separator) )))
		{
			if (!found && s.contains( "{" ))
				found = true;
			if (found)
				afterPattern.add( s );
			else
				beforePattern.add( s );
		}
		String prefix = String.join( File.separator, beforePattern );
		String pattern = String.join( File.separator, afterPattern );
		return new ValuePair< String, String >( prefix, pattern );
	}

	public static String getRangeRepresentation(Pair<Integer, Integer> range)
	{
		if (range.getA().equals( range.getB() ))
			return Integer.toString( range.getA() );
		else
			if (range.getA() < range.getB())
				return range.getA() + "-" + range.getB();
			else
				return range.getB() + "-" + range.getA();
	}

	public static Pair<String, String> splitIntoPathAndPattern(String s, String ... templates)
	{
		String[] subpaths = s.split( Pattern.quote(File.separator) );
		ArrayList<String> path = new ArrayList<>(); 
		ArrayList<String> pattern = new ArrayList<>();
		boolean noPatternFound = true;

		for (int i = 0; i < subpaths.length; i++){
			if (noPatternFound && !containsAny( subpaths[i], templates ))
			{
				path.add( subpaths[i] );
			}
			else
			{
				noPatternFound = false;
				pattern.add(subpaths[i]);
			}
		}
		
		String sPath = String.join( "/", path );
		String sPattern = String.join( "/", pattern );
		
		return new ValuePair< String, String >( sPath, sPattern );
	}

}
