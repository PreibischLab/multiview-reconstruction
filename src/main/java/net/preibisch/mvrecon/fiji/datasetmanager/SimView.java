package net.preibisch.mvrecon.fiji.datasetmanager;

import java.awt.Font;
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
	public static boolean defaultModifyStackSize = false;
	public static boolean defaultModifyCal = false;

	public static String[] types = {"8-bit", "16-bit Signed", "16-bit Unsigned", "32-bit Signed", "32-bit Unsigned" };
	public static int defaultType = 2;
	public static boolean defaultLittleEndian = true;

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
		final SimViewMetaData metaData = parseMetaData( rootDir, expDir );

		if ( metaData == null )
		{
			IOFunctions.println( "Failed to load metadata." );
			return null;
		}

		//
		// user input
		//
		if ( !showDialogs( metaData ) )
			return null;

		// TODO Auto-generated method stub
		return null;
	}

	protected boolean showDialogs( final SimViewMetaData meta )
	{
		GenericDialog gd = new GenericDialog( "SimView Properties" );

		gd.addMessage( "Angles (" + meta.numAngles + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		gd.addMessage( "Channels (" + meta.numChannels + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int c = 0; c < meta.numChannels; ++c )
			gd.addStringField( "Channel_" + meta.channels[ c ] + ":", Integer.toString( meta.metaDataChannels[ c ].wavelength ) );

		gd.addMessage( "Timepoints (" + meta.numTimePoints + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		gd.addMessage( "Stack Sizes", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addNumericField("X_size", meta.stackSize[ 0 ], 0 );
		gd.addNumericField("Y_size", meta.stackSize[ 1 ], 0 );
		gd.addNumericField("Z_size", meta.stackSize[ 2 ], 0 );
		gd.addChoice("Image_type:", types, types[ defaultType ]);
		gd.addCheckbox("Little-endian byte order", defaultLittleEndian );

		gd.addMessage( "" );

		gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addCheckbox( "Modify_calibration", defaultModifyCal );
		gd.addMessage( "Pixel Distance X (guessed): " + meta.xStep + " um" );
		gd.addMessage( "Pixel Distance Y (guessed): " + meta.yStep + " um" );
		gd.addMessage( "Pixel Distance Z: " + meta.zStep + " um"  );

		gd.addMessage( "Additional Meta Data", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "Acquisition Objective: " + meta.metaDataChannels[ 0 ].metadataHash.getOrDefault("detection_objective", "<not stored>"), new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		gd.addMessage( "Specimen Name: " + meta.metaDataChannels[ 0 ].specimen_name, new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		gd.addMessage( "Time Stamp: " + meta.metaDataChannels[ 0 ].timestamp, new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		return true;
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

		if ( metaData.assignGlobalValues() )
			return metaData;
		else
			return null;
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
