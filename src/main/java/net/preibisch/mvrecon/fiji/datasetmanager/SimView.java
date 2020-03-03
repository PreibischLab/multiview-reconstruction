package net.preibisch.mvrecon.fiji.datasetmanager;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;

import org.jdom2.JDOMException;

import bdv.BigDataViewer;
import bdv.viewer.ViewerOptions;
import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.datasetmanager.SimViewMetaData.SimViewChannel;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class SimView implements MultiViewDatasetDefinition
{
	public static String defaultDir = "";
	public static String defaultItem = "";
	
	@Override
	public SpimData2 createDataset()
	{
		final File rootDir = queryRootDir();

		//
		// Query root dir
		// 
		if ( rootDir == null )
		{
			IOFunctions.println( "Root dir not defined. stopping.");
			return null;
		}
		else
		{
			IOFunctions.println( "Root dir = '" + rootDir.getAbsolutePath() + "'.");
		}

		//
		// Query experiment dir (if necessary)
		// 
		final File expDir = getExperimentDir( rootDir );
		
		if ( expDir == null )
		{
			IOFunctions.println( "Experiment dir not defined. stopping.");
			return null;
		}
		else
		{
			IOFunctions.println( "Experiment dir = '" + expDir.getAbsolutePath() + "'.");
		}

		//
		// Parse MetaData
		// 
		SimViewMetaData metaData = parseMetaData( rootDir, expDir );
		
		// TODO Auto-generated method stub
		return null;
	}

	protected SimViewMetaData parseMetaData( final File rootDir, final File expDir )
	{
		final SimViewMetaData metaData = new SimViewMetaData();
		metaData.rootDir = rootDir;
		metaData.expDir = expDir;

		//
		// get #timepoints from the sorted directory list
		//
		String[] dirs = expDir.list( new DirectoryFilter( "TM" ) );

		if ( dirs.length == 0 )
		{
			IOFunctions.println( expDir.getAbsolutePath() + " contains no subdirectories with experiments." );
			return null;
		}
		else
		{
			Arrays.sort( dirs );

			metaData.numTimePoints = dirs.length;
			metaData.timePoints = dirs;

			IOFunctions.println( "Found " + metaData.numTimePoints + " timepoints: " + metaData.timePoints[ 0 ] + " >>> " + metaData.timePoints[ metaData.timePoints.length - 1] + "." );
		}

		//
		// get #channels from the XML files in the first timepoint
		//
		final File firstTP = new File( expDir, metaData.timePoints[ 0 ] );
		dirs = firstTP.list( new FilenameFilter()
		{
			@Override
			public boolean accept(final File dir, final String name)
			{
				return name.toLowerCase().endsWith( ".xml");
			}
		});

		if ( dirs.length == 0 )
		{
			IOFunctions.println( expDir.getAbsolutePath() + " contains no XML files." );
			return null;
		}
		else
		{
			Arrays.sort( dirs );

			metaData.numChannels = dirs.length;
			metaData.metaDataChannels = new SimViewChannel[ metaData.numChannels ];
			metaData.channels = dirs;
			metaData.baseXMLs = new String[ dirs.length ];

			IOFunctions.println( "Found " + metaData.numChannels + " channels: " );

			for ( int c = 0; c < metaData.numChannels; ++c )
			{
				metaData.baseXMLs[ c ] = new File( metaData.timePoints[ 0 ], metaData.channels[ c ] ).getPath();
				metaData.channels[ c ] = metaData.channels[ c ].substring( 0, metaData.channels[ c ].toLowerCase().lastIndexOf(".xml") );

				IOFunctions.println();
				IOFunctions.println( "channel " + metaData.channels[ c ] );
				IOFunctions.println( "baseXML " + metaData.baseXMLs[ c ] );

				try
				{
					metaData.metaDataChannels[ c ] = SimViewMetaData.parseSimViewXML( new File( expDir, metaData.baseXMLs[ c ] ) );
				}
				catch (JDOMException | IOException e)
				{
					IOFunctions.println( "Failed to parse XML: " + e );
					IOFunctions.println( "Stopping." );
					e.printStackTrace();
					return null;
				}
			}

			//
			// get #rotation angles from the directory structure
			//
			dirs = firstTP.list( new FilenameFilter()
			{
				@Override
				public boolean accept(final File dir, final String name)
				{
					return name.toLowerCase().startsWith( "ang" ) && new File( dir, name ).isDirectory();
				}
			});

			Arrays.sort( dirs );
			metaData.numAngles = dirs.length;
			metaData.angles = dirs;
			
			IOFunctions.println();
			IOFunctions.println( "Found " + metaData.numAngles + " angles: " );
			
			for ( final String angle : metaData.angles )
				IOFunctions.println( angle );
		}
		
		return metaData;
	}
	
	public static class DirectoryFilter implements FilenameFilter
	{
		private final String startsWith;

		public DirectoryFilter() { this.startsWith = null; }

		public DirectoryFilter( final String startsWith ) { this.startsWith = startsWith; }

		@Override
		public boolean accept( final File dir, final String name )
		{
			final File f = new File( dir, name );

			if ( f.isDirectory() && ( startsWith == null || name.startsWith(startsWith) ) )
				return true;
			else
				return false;
		}	
	}
	
	protected File getExperimentDir( final File rootDir )
	{
		final String[] dirs = rootDir.list( new DirectoryFilter() );

		if ( dirs.length == 0 )
		{
			IOFunctions.println( rootDir.getAbsolutePath() + " contains no subdirectories with experiments." );
			return null;
		}
		else if ( dirs.length == 1 )
		{
			return new File( rootDir, dirs[ 0 ] );
		}
		else
		{
			Arrays.sort( dirs );

			GenericDialog gd = new GenericDialog( "Select experiment to import" );

			boolean contains = false;

			for ( final String dir : dirs )
				if ( dir.equals( defaultItem ) )
					contains = true;

			if ( defaultItem.length() == 0 || !contains )
				defaultItem = dirs[ 0 ];
			
			gd.addChoice( "Experiment", dirs, defaultItem );
			//gd.addRadioButtonGroup("Experiment", dirs, 1, dirs.length, defaultItem );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return null;

			return new File( rootDir, defaultItem = gd.getNextChoice() );
		}
	}

	protected File queryRootDir()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "SimView Data Directory" );
	
		gd.addDirectoryField( "SimView data directory", defaultDir, 50);
	
		gd.showDialog();
	
		if ( gd.wasCanceled() )
			return null;
	
		final File dir = new File( defaultDir = gd.getNextString() );
	
		if ( !dir.exists() )
		{
			IOFunctions.println( "Directory '" + dir.getAbsolutePath() + "' does not exist. Stopping" );
			return null;
		}
		else
		{
			IOFunctions.println( "Investigating directory '" + dir.getAbsolutePath() + "'." );
			return dir;
		}
	}


	@Override
	public String getExtendedDescription()
	{
		return "This dataset definition parses a directory structure\n" +
			   "saved by SimView-like microscopes from LabView.";
	}

	@Override
	public String getTitle() { return "SimView Dataset Loader (Raw)"; }

	@Override
	public MultiViewDatasetDefinition newInstance() { return new SimView(); }

	public static void main( String[] args )
	{
		defaultDir = "/nrs/aic/Wait/for_stephan/Run2_20190909_155416";

		SpimData2 sd = new SimView().createDataset();

		if ( sd == null )
			IOFunctions.println( "Failed to define dataset.");
		else
			BigDataViewer.open(  sd, "", null, ViewerOptions.options() );
	}

}
