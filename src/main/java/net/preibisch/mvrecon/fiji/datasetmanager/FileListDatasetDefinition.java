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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.swing.JLabel;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
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
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.AngleInfo;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.ChannelInfo;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.CheckResult;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileInfo;
import net.preibisch.mvrecon.fiji.datasetmanager.grid.RegularTranformHelpers;
import net.preibisch.mvrecon.fiji.datasetmanager.grid.RegularTranformHelpers.RegularTranslationParameters;
import net.preibisch.mvrecon.fiji.datasetmanager.patterndetector.FilenamePatternDetector;
import net.preibisch.mvrecon.fiji.datasetmanager.patterndetector.NumericalFilenamePatternDetector;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.ProgressWriterIJ;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.explorer.util.ColorStream;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.FileMapImgLoaderLOCI;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LegacyFileMapImgLoaderLOCI;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2.FileMapGettable;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2.FileMapImgLoaderLOCI2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class FileListDatasetDefinition implements MultiViewDatasetDefinition
{
	public static final String[] GLOB_SPECIAL_CHARS = new String[] {"{", "}", "[", "]", "*", "?"};
	
	private static ArrayList<FileListChooser> fileListChoosers = new ArrayList<>();
	static
	{
		fileListChoosers.add( new WildcardFileListChooser() );
		//fileListChoosers.add( new SimpleDirectoryFileListChooser() );
	}
	
	private static interface FileListChooser
	{
		public List<File> getFileList();
		public String getDescription();
		public FileListChooser getNewInstance();
	}
	
	private static class WildcardFileListChooser implements FileListChooser
	{

		private static long KB_FACTOR = 1024;
		private static int minNumLines = 10;
		private static String info = "<html> <h1> Select files via wildcard expression </h1> <br /> "
				+ "Use the path field to specify a file or directory to process or click 'Browse...' to select one. <br /> <br />"
				+ "Wildcard (*) expressions are allowed. <br />"
				+ "e.g. '/Users/spim/data/spim_TL*_Angle*.tif' <br /><br />"
				+ "</html>";
		
		
		private static String previewFiles(List<File> files){
			StringBuilder sb = new StringBuilder();
			sb.append("<html><h2> selected files </h2>");
			for (File f : files)
				sb.append( "<br />" + f.getAbsolutePath() );
			for (int i = 0; i < minNumLines - files.size(); i++)
				sb.append( "<br />"  );
			sb.append( "</html>" );
			return sb.toString();
		}
		
		
		
		@Override
		public List< File > getFileList()
		{

			GenericDialogPlus gdp = new GenericDialogPlus("Pick files to include");

			addMessageAsJLabel(info, gdp);

			gdp.addDirectoryOrFileField( "path", "/", 65);
			gdp.addNumericField( "exclude files smaller than (KB)", 10, 0 );

			// preview selected files - not possible in headless
			if (!PluginHelper.isHeadless())
				{
				// add empty preview
				addMessageAsJLabel(previewFiles( new ArrayList<>()), gdp,  GUIHelper.smallStatusFont);

				JLabel lab = (JLabel)gdp.getComponent( 5 );
				TextField num = (TextField)gdp.getComponent( 4 ); 
				Panel pan = (Panel)gdp.getComponent( 2 );

				num.addTextListener( new TextListener()
				{

					@Override
					public void textValueChanged(TextEvent e)
					{
						String path = ((TextField)pan.getComponent( 0 )).getText();

						System.out.println(path);
						if (path.endsWith( File.separator ))
							path = path.substring( 0, path.length() - File.separator.length() );

						if(new File(path).isDirectory())
							path = String.join( File.separator, path, "*" );

						lab.setText( previewFiles( getFilesFromPattern(path , Long.parseLong( num.getText() ) * KB_FACTOR)));
						lab.setSize( lab.getPreferredSize() );
						gdp.setSize( gdp.getPreferredSize() );
						gdp.validate();
					}
				} );

				((TextField)pan.getComponent( 0 )).addTextListener( new TextListener()
				{

					@Override
					public void textValueChanged(TextEvent e)
					{
						String path = ((TextField)pan.getComponent( 0 )).getText();
						if (path.endsWith( File.separator ))
							path = path.substring( 0, path.length() - File.separator.length() );

						if(new File(path).isDirectory())
							path = String.join( File.separator, path, "*" );

						lab.setText( previewFiles( getFilesFromPattern(path , Long.parseLong( num.getText() ) * KB_FACTOR)));
						lab.setSize( lab.getPreferredSize() );
						gdp.setSize( gdp.getPreferredSize() );
						gdp.validate();
					}
				} );
			}

			GUIHelper.addScrollBars( gdp );
			gdp.showDialog();

			if (gdp.wasCanceled())
				return new ArrayList<>();

			String fileInput = gdp.getNextString();

			if (fileInput.endsWith( File.separator ))
				fileInput = fileInput.substring( 0, fileInput.length() - File.separator.length() );

			if(new File(fileInput).isDirectory())
				fileInput = String.join( File.separator, fileInput, "*" );

			List<File> files = getFilesFromPattern( fileInput, (long) gdp.getNextNumber() * KB_FACTOR );

			files.forEach(f -> System.out.println( "Including file " + f + " in dataset." ));

			return files;
		}

		@Override
		public String getDescription(){return "Choose via wildcard expression";}

		@Override
		public FileListChooser getNewInstance() {return new WildcardFileListChooser();}
		
	}
	
	private static class SimpleDirectoryFileListChooser implements FileListChooser
	{

		@Override
		public List< File > getFileList()
		{
			List< File > res = new ArrayList<File>();
			
			DirectoryChooser dc = new DirectoryChooser ( "pick directory" );
			if (dc.getDirectory() != null)
				try
				{
					res = Files.list( Paths.get( dc.getDirectory() ))
						.filter(p -> {
							try
							{
								if ( Files.size( p ) > 10 * 1024 )
									return true;
								else
									return false;
							}
							catch ( IOException e )
							{
								// TODO Auto-generated catch block
								e.printStackTrace();
								return false;
							}
						}
						).map( p -> p.toFile() ).collect( Collectors.toList() );
					
				}
				catch ( IOException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			return res;
			
			
		}
		
		

		@Override
		public String getDescription()
		{
			// TODO Auto-generated method stub
			return "select a directory manually";
		}

		@Override
		public FileListChooser getNewInstance()
		{
			// TODO Auto-generated method stub
			return new SimpleDirectoryFileListChooser();
		}
		
	}
	
	public static void addMessageAsJLabel(String msg, GenericDialog gd)
	{
		addMessageAsJLabel(msg, gd, null);
	}
	
	public static void addMessageAsJLabel(String msg, GenericDialog gd, Font font)
	{
		addMessageAsJLabel(msg, gd, font, null);
	}

	public static void addMessageAsJLabel(String msg, GenericDialog gd, Font font, Color color)
	{
		gd.addMessage( msg );
		if (!PluginHelper.isHeadless())
		{
			final Component msgC = gd.getComponent(gd.getComponentCount() - 1 );
			final JLabel msgLabel = new JLabel(msg);

			if (font!=null)
				msgLabel.setFont(font);
			if (color!=null)
				msgLabel.setForeground(color);

			gd.add(msgLabel);
			GridBagConstraints constraints = ((GridBagLayout)gd.getLayout()).getConstraints(msgC);
			((GridBagLayout)gd.getLayout()).setConstraints(msgLabel, constraints);

			gd.remove(msgC);
		}
	}
	
		
	
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
	
	private static SpimData2 buildSpimData( FileListViewDetectionState state, boolean withVirtualLoader )
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
			imgLoader = new FileMapImgLoaderLOCI2( fileMap, FileListDatasetDefinitionUtil.selectImgFactory(state.getDimensionMap()), sd );
		else
			imgLoader = new FileMapImgLoaderLOCI( fileMap, FileListDatasetDefinitionUtil.selectImgFactory(state.getDimensionMap()), sd );
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

		SpimData2 data = new SpimData2( new File("/"), sd, vrs, viewInterestPoints, new BoundingBoxes(), new PointSpreadFunctions(), new StitchingResults() );
		return data;
	}


	@Override
	public SpimData2 createDataset( )
	{

		FileListChooser chooser = fileListChoosers.get( 0 );

		// only ask how we want to choose files if there are multiple ways
		if (fileListChoosers.size() > 1)
		{
			String[] fileListChooserChoices = new String[fileListChoosers.size()];
			for (int i = 0; i< fileListChoosers.size(); i++)
				fileListChooserChoices[i] = fileListChoosers.get( i ).getDescription();

			GenericDialog gd1 = new GenericDialog( "How to select files" );
			gd1.addChoice( "file chooser", fileListChooserChoices, fileListChooserChoices[0] );
			gd1.showDialog();

			if (gd1.wasCanceled())
				return null;

			chooser = fileListChoosers.get( gd1.getNextChoiceIndex() );
		}

		List<File> files = chooser.getFileList();

		FileListViewDetectionState state = new FileListViewDetectionState();
		FileListDatasetDefinitionUtil.detectViewsInFiles( files, state);

		Map<Class<? extends Entity>, List<Integer>> fileVariableToUse = new HashMap<>();
		List<String> choices = new ArrayList<>();

		FilenamePatternDetector patternDetector = new NumericalFilenamePatternDetector();
		patternDetector.detectPatterns( files );
		int numVariables = patternDetector.getNumVariables();

		StringBuilder inFileSummarySB = new StringBuilder();
		inFileSummarySB.append( "<html> <h2> Views detected in files </h2>" );

		// summary timepoints
		if (state.getMultiplicityMap().get( TimePoint.class ) == CheckResult.SINGLE)
		{
//			inFileSummarySB.append( "<p> No timepoints detected within files </p>" );
			choices.add( "TimePoints" );
		}
		else if (state.getMultiplicityMap().get( TimePoint.class ) == CheckResult.MULTIPLE_INDEXED)
		{
			int numTPs = (Integer) state.getAccumulateMap( TimePoint.class ).keySet().stream().reduce(0, (x,y) -> Math.max( (Integer) x, (Integer) y) );
			inFileSummarySB.append( "<p style=\"color:green\">" + numTPs+ " timepoints detected within files </p>" );
			if (state.getAccumulateMap( TimePoint.class ).size() > 1)
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: Number of timepoints is not the same for all views </p>" );
		}

		inFileSummarySB.append( "<br />" );

		// we might want to know how many channels/illums or tiles/angles to expect even though we have no metadata
		// NB: dont use these results if there IS metadata
		final Pair< Integer, Integer > minMaxNumCannelsIndexed = FileListViewDetectionState.getMinMaxNumChannelsIndexed( state );
		final Pair< Integer, Integer > minMaxNumSeriesIndexed = FileListViewDetectionState.getMinMaxNumSeriesIndexed( state );

		// summary channel
		if (state.getMultiplicityMap().get( Channel.class ) == CheckResult.SINGLE)
		{
			inFileSummarySB.append( !state.getAmbiguousIllumChannel() ? "" : "<p>"+ getRangeRepresentation( minMaxNumCannelsIndexed ) + " Channels OR Illuminations detected within files </p>");
			choices.add( "Channels" );
		}
		else if (state.getMultiplicityMap().get( Channel.class ) == CheckResult.MULTIPLE_INDEXED)
		{

			inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumCannelsIndexed ) + " Channels detected within files </p>" );
			inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no metadata was found for Channels </p>" );
			if (state.getMultiplicityMap().get( Illumination.class ) == CheckResult.MULTIPLE_INDEXED)
			{
				choices.add( "Channels" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no matadata for Illuminations found either, cannot distinguish </p>" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: choose manually whether files contain Channels or Illuminations below </p>" );
			}
		} else if (state.getMultiplicityMap().get( Channel.class ) == CheckResult.MUlTIPLE_NAMED)
		{
			int numChannels = state.getAccumulateMap( Channel.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numChannels + " Channels found within files </p>" );
		}

		//inFileSummarySB.append( "<br />" );

		// summary illum
		if ( state.getMultiplicityMap().get( Illumination.class ) == CheckResult.SINGLE )
		{
//			if (!state.getAmbiguousIllumChannel())
//				inFileSummarySB.append( "<p> No illuminations detected within files </p>" );
			choices.add( "Illuminations" );
		}
		else if ( state.getMultiplicityMap().get( Illumination.class ) == CheckResult.MULTIPLE_INDEXED )
		{
			if (state.getMultiplicityMap().get( Channel.class ).equals( CheckResult.MULTIPLE_INDEXED ))
				choices.add( "Illuminations" );
			else
				inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumCannelsIndexed ) + " Illuminations detected within files </p>" );
		}
		else if ( state.getMultiplicityMap().get( Illumination.class ) == CheckResult.MUlTIPLE_NAMED )
		{
			int numIllum = state.getAccumulateMap( Illumination.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numIllum + " Illuminations found within files </p>" );
		}
		
		//inFileSummarySB.append( "<br />" );
		
		// summary tile
		if ( state.getMultiplicityMap().get( Tile.class ) == CheckResult.SINGLE )
		{
//			inFileSummarySB.append( "<p> No tiles detected within files </p>" );
			choices.add( "Tiles" );
		}
		else if ( state.getMultiplicityMap().get( Tile.class ) == CheckResult.MULTIPLE_INDEXED )
		{
			inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumSeriesIndexed ) + " Tiles detected within files </p>" );
			inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no metadata was found for Tiles </p>" );
			if (state.getMultiplicityMap().get( Angle.class ) == CheckResult.MULTIPLE_INDEXED)
			{
				choices.add( "Tiles" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no metadata for Angles found either, cannot distinguish </p>" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: choose manually wether files contain Tiles or Angles below </p>" );
			}
		}
		else if ( state.getMultiplicityMap().get( Tile.class ) == CheckResult.MUlTIPLE_NAMED )
		{
			int numTile = state.getAccumulateMap( Tile.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numTile + " Tiles found within files </p>" );
			
		}
		
		//inFileSummarySB.append( "<br />" );
		
		// summary angle
		if ( state.getMultiplicityMap().get( Angle.class ) == CheckResult.SINGLE )
		{
//			inFileSummarySB.append( "<p> No angles detected within files </p>" );
			choices.add( "Angles" );
		}
		else if ( state.getMultiplicityMap().get( Angle.class ) == CheckResult.MULTIPLE_INDEXED )
		{
			if (state.getMultiplicityMap().get( Tile.class ) == CheckResult.MULTIPLE_INDEXED)
				choices.add( "Angles" );
			else
				inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumSeriesIndexed ) + " Angles detected within files </p>" );
		}
		else if ( state.getMultiplicityMap().get( Angle.class ) == CheckResult.MUlTIPLE_NAMED )
		{
			int numAngle = state.getAccumulateMap( Angle.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numAngle + " Angles found within files </p>" );
		}

		inFileSummarySB.append( "</html>" );

		GenericDialogPlus gd = new GenericDialogPlus("Define Metadata for Views");
		
		//gd.addMessage( "<html> <h1> View assignment </h1> </html> ");
		//addMessageAsJLabel( "<html> <h1> View assignment </h1> </html> ", gd);
		
		//gd.addMessage( inFileSummarySB.toString() );
		addMessageAsJLabel(inFileSummarySB.toString(), gd);
		
		String[] choicesAngleTile = new String[] {"Angles", "Tiles"};
		String[] choicesChannelIllum = new String[] {"Channels", "Illuminations"};

		//if (state.getAmbiguousAngleTile())
		String preferedAnglesOrTiles = state.getMultiplicityMap().get( Angle.class ) == CheckResult.MULTIPLE_INDEXED ? "Angles" : "Tiles";
		if (state.getAmbiguousAngleTile() || state.getMultiplicityMap().get( Tile.class) == CheckResult.MUlTIPLE_NAMED)
			gd.addChoice( "BioFormats_Series_are?", choicesAngleTile, preferedAnglesOrTiles );
		if (state.getAmbiguousIllumChannel())
			gd.addChoice( "BioFormats_Channels_are?", choicesChannelIllum, choicesChannelIllum[0] );


		if (numVariables >= 1)
//			sbfilePatterns.append( "<p> No numerical patterns found in filenames</p>" );
//		else
		{
			final Pair< String, String > prefixAndPattern = splitIntoPrefixAndPattern( patternDetector );
			final StringBuilder sbfilePatterns = new StringBuilder();
			sbfilePatterns.append(  "<html> <h2> Patterns in filenames </h2> " );
			sbfilePatterns.append( "<h3 style=\"color:green\"> " + numVariables + ""
					+ " numerical pattern" + ((numVariables > 1) ? "s": "") + " found in filenames</h3>" );
			sbfilePatterns.append( "</br><p> Patterns: " + getColoredHtmlFromPattern( prefixAndPattern.getB(), false ) + "</p>" );
			sbfilePatterns.append( "</html>" );
			addMessageAsJLabel(sbfilePatterns.toString(), gd);
		}

		//gd.addMessage( sbfilePatterns.toString() );

		choices.add( "-- ignore this pattern --" );
		String[] choicesAll = choices.toArray( new String[]{} );

		for (int i = 0; i < numVariables; i++)
		{
			gd.addChoice( "Pattern_" + i + " represents", choicesAll, choicesAll[0] );
			//do not fail just due to coloring
			try
			{
				((Label) gd.getComponent( gd.getComponentCount() - 2 )).setForeground( getColorN( i ) );
			}
			catch (Exception e) {}
		}

		addMessageAsJLabel(  "<html> <h2> Voxel Size calibration </h2> </html> ", gd );
		final boolean allVoxelSizesTheSame = FileListViewDetectionState.allVoxelSizesTheSame( state );
		if(!allVoxelSizesTheSame)
			addMessageAsJLabel(  "<html> <p style=\"color:orange\">WARNING: Voxel Sizes are not the same for all views, modify them at your own risk! </p> </html> ", gd );

		final VoxelDimensions someCalib = state.getDimensionMap().values().iterator().next().getB();

		gd.addCheckbox( "Modify_voxel_size?", false );
		gd.addNumericField( "Voxel_size_X", someCalib.dimension( 0 ), 4 );
		gd.addNumericField( "Voxel_size_Y", someCalib.dimension( 1 ), 4 );
		gd.addNumericField( "Voxel_size_Z", someCalib.dimension( 2 ), 4 );
		gd.addStringField( "Voxel_size_unit", someCalib.unit() );

		// try to guess if we need to move to grid
		// we suggest move if: we have no tile metadata
		addMessageAsJLabel(  "<html> <h2> Move to Grid </h2> </html> ", gd );
		boolean haveTileLoc = state.getAccumulateMap( Tile.class ).keySet().stream().filter( t -> ((TileInfo)t).locationX != null && ((TileInfo)t).locationX != 0.0 ).findAny().isPresent();
		
		String[] choicesGridMove = new String[] {"Do not move Tiles to Grid (use Metadata if available)",
				"Move Tiles to Grid (interactive)", "Move Tile to Grid (Macro-scriptable)"};
		gd.addChoice( "Move_Tiles_to_Grid_(per_Angle)?", choicesGridMove, choicesGridMove[!haveTileLoc ? 1 : 0] );

		gd.showDialog();

		if (gd.wasCanceled())
			return null;

		boolean preferAnglesOverTiles = true;
		boolean preferChannelsOverIlluminations = true;
		if (state.getAmbiguousAngleTile() || state.getMultiplicityMap().get( Tile.class) ==  CheckResult.MUlTIPLE_NAMED)
			preferAnglesOverTiles = gd.getNextChoiceIndex() == 0;
		if (state.getAmbiguousIllumChannel())
			preferChannelsOverIlluminations = gd.getNextChoiceIndex() == 0;

		fileVariableToUse.put( TimePoint.class, new ArrayList<>() );
		fileVariableToUse.put( Channel.class, new ArrayList<>() );
		fileVariableToUse.put( Illumination.class, new ArrayList<>() );
		fileVariableToUse.put( Tile.class, new ArrayList<>() );
		fileVariableToUse.put( Angle.class, new ArrayList<>() );

		for (int i = 0; i < numVariables; i++)
		{
			String choice = gd.getNextChoice();
			if (choice.equals( "TimePoints" ))
				fileVariableToUse.get( TimePoint.class ).add( i );
			else if (choice.equals( "Channels" ))
				fileVariableToUse.get( Channel.class ).add( i );
			else if (choice.equals( "Illuminations" ))
				fileVariableToUse.get( Illumination.class ).add( i );
			else if (choice.equals( "Tiles" ))
				fileVariableToUse.get( Tile.class ).add( i );
			else if (choice.equals( "Angles" ))
				fileVariableToUse.get( Angle.class ).add( i );
		}


		// TODO handle Angle-Tile swap here	
		FileListDatasetDefinitionUtil.resolveAmbiguity( state.getMultiplicityMap(), state.getAmbiguousIllumChannel(), preferChannelsOverIlluminations, state.getAmbiguousAngleTile(), !preferAnglesOverTiles );

		FileListDatasetDefinitionUtil.expandAccumulatedViewInfos(
				fileVariableToUse, 
				patternDetector,
				state);

		// query modified calibration
		final boolean modifyCalibration = gd.getNextBoolean();
		if (modifyCalibration)
		{
			final double calX = gd.getNextNumber();
			final double calY = gd.getNextNumber();
			final double calZ = gd.getNextNumber();
			final String calUnit = gd.getNextString();

			for (final Pair< File, Pair< Integer, Integer > > key : state.getDimensionMap().keySet())
			{
				final Pair< Dimensions, VoxelDimensions > pairOld = state.getDimensionMap().get( key );
				final Pair< Dimensions, VoxelDimensions > pairNew = new ValuePair< Dimensions, VoxelDimensions >( pairOld.getA(), new FinalVoxelDimensions( calUnit, calX, calY, calZ ) );
				state.getDimensionMap().put( key, pairNew );
			}
		}

		final int gridMoveType = gd.getNextChoiceIndex();

		// we create a virtual SpimData at first
		SpimData2 data = buildSpimData( state, true );

		// we move to grid, collect parameters first
		final List<RegularTranslationParameters> gridParams = new ArrayList<>();
		if (gridMoveType == 2)
		{
			final ArrayList<ViewDescription> vds = new ArrayList<>(data.getSequenceDescription().getViewDescriptions().values());

			final Set<Class<? extends Entity>> angleClassSet = new HashSet<>();
			angleClassSet.add( Angle.class );
			final Set<Class<? extends Entity>> tileClassSet = new HashSet<>();
			tileClassSet.add( Tile.class );

			// first, split by angles (we process each angle separately)
			final List< Group< ViewDescription > > vdsAngleGrouped = Group.splitBy( vds , angleClassSet );
			for (Group<ViewDescription> vdsAngle : vdsAngleGrouped)
			{
				// second, we split by tiles (all channels/illums/tps of a tile are grouped)
				final List< Group< ViewDescription > > tilesGrouped = Group.splitBy( new ArrayList<>( vdsAngle.getViews() ), tileClassSet );
				final String angleName = vdsAngle.getViews().iterator().next().getViewSetup().getAngle().getName();
				if (tilesGrouped.size() < 2)
					continue;

				final RegularTranslationParameters params = RegularTranformHelpers.queryParameters( "Move Tiles of Angle " + angleName, tilesGrouped.size() );
				
				if ( params == null )
					return null;

				gridParams.add( params );
			}
		}

		//TODO: with translated tiles, we also have to take the center of rotation into account
		//Apply_Transformation.applyAxis( data );

		GenericDialogPlus gdSave = new GenericDialogPlus( "Save dataset definition" );

		//gdSave.addMessage( "<html> <h1> Saving options </h1> <br /> </html>" );
		addMessageAsJLabel("<html> <h1> Saving options </h1> <br /> </html>", gdSave);

		gdSave.addCheckbox( "Use_virtual_images_(cached)", true );

//		if (!useVirtualLoader)
//		{
//			Class<?> imgFactoryClass = ((FileMapImgLoaderLOCI)data.getSequenceDescription().getImgLoader() ).getImgFactory().getClass();
//			if (imgFactoryClass.equals( CellImgFactory.class ))
//			{
//				//gdSave.addMessage( "<html> <h2> ImgLib2 container </h2> <br/>"
//				//		+ "<p style=\"color:orange\"> Some views of the dataset are larger than 2^31 pixels, will use CellImg </p>" );
//				addMessageAsJLabel("<html> <h2> ImgLib2 container </h2> <br/>"
//						+ "<p style=\"color:orange\"> Some views of the dataset are larger than 2^31 pixels, will use CellImg </p>", gdSave);
//			}
//			else
//			{
//				//gdSave.addMessage( "<html> <h2> ImgLib2 container </h2> <br/>");
//				addMessageAsJLabel("<html> <h2> ImgLib2 container </h2> <br/>", gdSave);
//				String[] imglibChoice = new String[] {"ArrayImg", "CellImg"};
//				gdSave.addChoice( "imglib2 container", imglibChoice, imglibChoice[0] );
//			}
//		}

		//gdSave.addMessage("<html><h2> Save path </h2></html>");
		addMessageAsJLabel("<html><h2> Save path </h2></html>", gdSave);

		Set<String> filenames = new HashSet<>();
		((FileMapGettable)data.getSequenceDescription().getImgLoader() ).getFileMap().values().stream().forEach(
				p -> 
				{
					filenames.add( p.getA().getAbsolutePath());
					System.out.println( p.getA().getAbsolutePath() );
				});

		File prefixPath;
		if (filenames.size() > 1)
			prefixPath = getLongestPathPrefix( filenames );
		else
		{
			String fi = filenames.iterator().next();
			prefixPath = new File((String)fi.subSequence( 0, fi.lastIndexOf( File.separator )));
		}

		gdSave.addDirectoryField( "dataset_save_path", prefixPath.getAbsolutePath(), 55 );

		// check if all stack sizes are the same (in each file)
		boolean zSizeEqualInEveryFile = LegacyFileMapImgLoaderLOCI.isZSizeEqualInEveryFile( data, (FileMapGettable)data.getSequenceDescription().getImgLoader() );

		// only consider if there are actually multiple angles/tiles
		zSizeEqualInEveryFile = zSizeEqualInEveryFile && !(data.getSequenceDescription().getAllAnglesOrdered().size() == 1 && data.getSequenceDescription().getAllTilesOrdered().size() == 1);
		// notify user if all stacks are equally size (in every file)
		if (zSizeEqualInEveryFile)
		{
			addMessageAsJLabel( "<html><p style=\"color:orange\">WARNING: all stacks have the same size, this might be caused by a bug"
					+ " in BioFormats. </br> Please re-check stack sizes if necessary.</p></html>", gdSave );

			// default choice for size re-check: do it if all stacks are the same size
			gdSave.addCheckbox( "check_stack_sizes", zSizeEqualInEveryFile );
		}

		gdSave.addCheckbox( "resave_as_HDF5", true );

		gdSave.showDialog();
		
		if ( gdSave.wasCanceled() )
			return null;

		final boolean useVirtualLoader = gdSave.getNextBoolean();
		// re-build the SpimData if user explicitly doesn't want virtual loading
		if (!useVirtualLoader)
			data = buildSpimData( state, useVirtualLoader );

//		if (!useVirtualLoader)
//		{
//			Class<?> imgFactoryClass = ((FileMapImgLoaderLOCI)data.getSequenceDescription().getImgLoader() ).getImgFactory().getClass();
//			if (!imgFactoryClass.equals( CellImgFactory.class ))
//			{
//				if (gdSave.getNextChoiceIndex() != 0)
//					((FileMapImgLoaderLOCI)data.getSequenceDescription().getImgLoader() ).setImgFactory( new CellImgFactory<>(256) );
//			}
//		}

		File chosenPath = new File( gdSave.getNextString());
		data.setBasePath( chosenPath );

		// check and correct stack sizes (the "BioFormats bug")
		// TODO: remove once the bug is fixed upstream
		if (zSizeEqualInEveryFile)
		{
			final boolean checkSize = gdSave.getNextBoolean();
			if (checkSize)
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Checking file sizes ... " );
				LegacyFileMapImgLoaderLOCI.checkAndRemoveZeroVolume( data, (ImgLoader & FileMapGettable) data.getSequenceDescription().getImgLoader() );
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Finished." );
			}
		}

		// now, we have a working SpimData and have corrected for unequal z sizes -> do grid move if necessary
		if (gridMoveType == 2)
		{
			final ArrayList<ViewDescription> vds = new ArrayList<>(data.getSequenceDescription().getViewDescriptions().values());

			final Set<Class<? extends Entity>> angleClassSet = new HashSet<>();
			angleClassSet.add( Angle.class );
			final Set<Class<? extends Entity>> tileClassSet = new HashSet<>();
			tileClassSet.add( Tile.class );

			// first, split by angles (we process each angle separately)
			final List< Group< ViewDescription > > vdsAngleGrouped = Group.splitBy( vds , angleClassSet );
			int i = 0;
			for (Group<ViewDescription> vdsAngle : vdsAngleGrouped)
			{
				// second, we split by tiles (all channels/illums/tps of a tile are grouped)
				final List< Group< ViewDescription > > tilesGrouped = Group.splitBy( new ArrayList<>( vdsAngle.getViews() ), tileClassSet );
				if (tilesGrouped.size() < 2)
					continue;
				RegularTranslationParameters gridParamsI = gridParams.get( i++ );
				RegularTranformHelpers.applyToSpimData( data, tilesGrouped, gridParamsI, true );
			}
		}

		boolean resaveAsHDF5 = gdSave.getNextBoolean();

		if (resaveAsHDF5)
		{
			final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( data.getSequenceDescription().getViewSetupsOrdered() );
			final int firstviewSetupId = data.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
			Generic_Resave_HDF5.lastExportPath = String.join( File.separator, chosenPath.getAbsolutePath(), "dataset");
			final Parameters params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, true );

			// HDF5 options dialog was cancelled
			if (params == null)
				return null;

			final ProgressWriter progressWriter = new ProgressWriterIJ();
			progressWriter.out().println( "starting export..." );
			
			Generic_Resave_HDF5.writeHDF5( data, params, progressWriter );
			
			System.out.println( "HDF5 resave finished." );
			
			net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair< SpimData2, List< String > > result = Resave_HDF5.createXMLObject( data, new ArrayList<>(data.getSequenceDescription().getViewDescriptions().keySet()), params, progressWriter, true );

			// ensure progressbar is gone
			progressWriter.setProgress( 1.0 );

			data = result.getA();
		}
		
		if (gridMoveType == 1)
		{
			data.gridMoveRequested = true;
		}
		
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

	@Override
	public String getTitle() { return "Automatic Loader (Bioformats based)"; }
	
	@Override
	public String getExtendedDescription()
	{
		return "This datset definition tries to automatically detect views in a\n" +
				"list of files openable by BioFormats. \n" +
				"If there are multiple Images in one file, it will try to guess which\n" +
				"views they belong to from meta data or ask the user for advice.\n";
	}


	@Override
	public MultiViewDatasetDefinition newInstance()
	{
		return new FileListDatasetDefinition();
	}
	
	
	public static boolean containsAny(String s, String ... templates)
	{
		for (int i = 0; i < templates.length; i++)
			if (s.contains( templates[i] ))
				return true;
		return false;
	}


	public static String getColoredHtmlFromPattern(String pattern, boolean withRootTag)
	{
		final StringBuilder sb = new StringBuilder();
		if (withRootTag)
			sb.append( "<html>" );
		int n = 0;
		for (int i = 0; i<pattern.length(); i++)
		{
			if (pattern.charAt( i ) == '{')
			{
				Color col = getColorN( n++ );
				sb.append( "<span style=\"color: rgb("+ col.getRed() + "," + col.getGreen() + "," + col.getBlue()   +")\">{" );
			}
			else if (pattern.charAt( i ) == '}')
				sb.append( "}</span>");
			else
				sb.append( pattern.charAt( i ) );
		}
		if (withRootTag)
			sb.append( "</html>" );
		return sb.toString();
	}
	
	public static Color getColorN(long n)
	{
		Iterator< ARGBType > iterator = ColorStream.iterator();
		ARGBType c = new ARGBType();
		for (int i = 0; i<n+43; i++)
			for (int j = 0; j<3; j++)
				c = iterator.next();
		return new Color( ARGBType.red( c.get() ), ARGBType.green( c.get() ), ARGBType.blue( c.get() ) );
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
	
	
	public static void main(String[] args)
	{
		//new FileListDatasetDefinition().createDataset();
		//new WildcardFileListChooser().getFileList().forEach( f -> System.out.println( f.getAbsolutePath() ) );
		GenericDialog gd = new GenericDialog( "A" );
		gd.addMessage( getColoredHtmlFromPattern( "a{b}c{d}e{aaaaaaaaaa}aa{bbbbbbbbbbbb}ccccc{ddddddd}", true ) );
		System.out.println( getColoredHtmlFromPattern( "a{b}c{d}e", false ) );
		gd.showDialog();
	}

}
