package net.preibisch.mvrecon.fiji.datasetmanager;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

import bdv.BigDataViewer;
import bdv.viewer.ViewerOptions;
import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class SimView implements MultiViewDatasetDefinition
{
	public static String defaultDir = "";
	
	@Override
	public SpimData2 createDataset()
	{
		final File rootDir = queryRootDir();

		if ( rootDir == null )
			return null;

		final File expDir = getExperimentDir( rootDir );
		
		if ( expDir == null )
			return null;

		IOFunctions.println( "Experiment dir = '" + expDir.getAbsolutePath() + "'.");
		// TODO Auto-generated method stub
		return null;
	}

	protected File getExperimentDir( final File rootDir )
	{
		final String[] dirs = rootDir.list( new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name)
			{
				final File f = new File( dir, name );
				if ( f.isDirectory() )
					return true;
				else
					return false;
			}
		});

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

			for ( final String dir : dirs )
				IOFunctions.println( dir );

			GenericDialog gd = new GenericDialog( "Select experiment" );
			return null;
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
		
		BigDataViewer.open(  sd, "", null, ViewerOptions.options() );
	}

}
