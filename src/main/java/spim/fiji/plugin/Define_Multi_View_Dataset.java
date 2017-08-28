package spim.fiji.plugin;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.event.ItemEvent;
import java.util.ArrayList;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.datasetmanager.DHM;
import spim.fiji.datasetmanager.FileListDatasetDefinition;
import spim.fiji.datasetmanager.LightSheetZ1;
import spim.fiji.datasetmanager.MicroManager;
import spim.fiji.datasetmanager.MultiViewDatasetDefinition;
import spim.fiji.datasetmanager.StackList;
import spim.fiji.datasetmanager.StackListImageJ;
import spim.fiji.datasetmanager.StackListLOCI;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.plugin.util.MyMultiLineLabel;
import spim.fiji.spimdata.SpimData2;

public class Define_Multi_View_Dataset implements PlugIn
{
	final public static ArrayList< MultiViewDatasetDefinition > staticDatasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();
	public static int defaultDatasetDef = 5;
	public static String defaultXMLName = "dataset.xml";

	final int numLinesDocumentation = 15;
	final int numCharacters = 80;
	
	static
	{
		IOFunctions.printIJLog = true;
		staticDatasetDefinitions.add( new StackListLOCI() );
		staticDatasetDefinitions.add( new StackListImageJ() );
		staticDatasetDefinitions.add( new MicroManager() );
		staticDatasetDefinitions.add( new LightSheetZ1() );
		staticDatasetDefinitions.add( new DHM() );
		staticDatasetDefinitions.add( new FileListDatasetDefinition() );
	}
	
	@Override
	public void run( String arg0 ) 
	{
		defineDataset( true );
	}

	public Pair< SpimData2, String > defineDataset( final boolean save )
	{
		final ArrayList< MultiViewDatasetDefinition > datasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();
		
		for ( final MultiViewDatasetDefinition mvd : staticDatasetDefinitions )
			datasetDefinitions.add( mvd.newInstance() );
		
		// verify that there are definitions
		final int numDatasetDefinitions = datasetDefinitions.size();
		
		if ( numDatasetDefinitions == 0 )
		{
			IJ.log( "No Multi-View Dataset Definitions available." );
			return null;
		}
		
		// get their names
		final String[] titles = new String[ numDatasetDefinitions ];
		
		for ( int i = 0; i < datasetDefinitions.size(); ++i )
			titles[ i ] = datasetDefinitions.get( i ).getTitle();
		
		// query the dataset definition to use
		final GenericDialogPlus gd1 = new GenericDialogPlus( "Select type of multi-view dataset" );

		if ( defaultDatasetDef >= numDatasetDefinitions )
			defaultDatasetDef = 0;
		
		gd1.addChoice( "Type_of_dataset: ", titles, titles[ defaultDatasetDef ] );
		//Choice choice = (Choice)gd1.getChoices().lastElement();
		gd1.addStringField( "XML_filename", defaultXMLName, 30 );
		/*
		final MyMultiLineLabel label = MyMultiLineLabel.addMessage( gd1,
				formatEntry( datasetDefinitions.get( defaultDatasetDef ).getExtendedDescription(), numCharacters, numLinesDocumentation ),
				new Font( Font.MONOSPACED, Font.PLAIN, 11 ),
				Color.BLACK );
						
		addListeners( gd1, choice, label, datasetDefinitions );*/
		
		GUIHelper.addWebsite( gd1 );
		
		gd1.showDialog();
		if ( gd1.wasCanceled() )
			return null;
		
		defaultDatasetDef = gd1.getNextChoiceIndex();
		final String xmlFileName = defaultXMLName = gd1.getNextString();
		
		// run the definition
		final MultiViewDatasetDefinition def = datasetDefinitions.get( defaultDatasetDef );
		
		IOFunctions.println( defaultDatasetDef );
		
		final SpimData2 spimData = def.createDataset();
		
		if ( spimData == null )
		{
			IOFunctions.println( "Defining multi-view dataset failed." );
			return null;
		}
		else
		{
			final String xml = SpimData2.saveXML( spimData, xmlFileName, "" );

			if ( xml != null )
			{
				GenericLoadParseQueryXML.defaultXMLfilename = xml;
				return new ValuePair< SpimData2, String >( spimData, xml );
			}
			else
			{
				return null;
			}
		}
	}
	
	public static String[] formatEntry( String line, final int numCharacters, final int numLines )
	{
		if ( line == null )
			line = "";
		
		String[] split = line.split( "\n" );
		
		if ( split.length != numLines )
		{
			String[] split2 = new String[ numLines ];

			for ( int j = 0; j < Math.min( split.length, numLines ); ++j )
				split2[ j ] = split[ j ];
			
			for ( int j = Math.min( split.length, numLines ); j < numLines; ++j )
				split2[ j ] = "";

			split = split2;
		}
		
		for ( int j = 0; j < split.length; ++j )
		{
			String s = split[ j ];
			
			if ( s.length() > 80 )
				s = s.substring( 0, 80 );
			
			// fill up to numCharacters + 3
			for ( int i = s.length(); i < numCharacters + 3; ++i )
				s = s + " ";
			
			split[ j ] = s;
		}
		
		return split;
	}

	protected void addListeners( final GenericDialog gd, final Choice choice, final MyMultiLineLabel label, final ArrayList< MultiViewDatasetDefinition > datasetDefinitions )
	{
		gd.addDialogListener( new DialogListener()
		{
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == choice )
				{
					label.setText( formatEntry( datasetDefinitions.get( choice.getSelectedIndex() ).getExtendedDescription(), numCharacters, numLinesDocumentation ) );
				}
				return true;
			}
		} );
		
	}
	
	public static void main( String args[] )
	{
		StackList.defaultDirectory = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM";

		IOFunctions.printIJLog = true;
		new ImageJ();
		new Define_Multi_View_Dataset().run( null );
		
		//System.exit( 0 );
	}
}
