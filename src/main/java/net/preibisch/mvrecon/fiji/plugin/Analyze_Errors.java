package net.preibisch.mvrecon.fiji.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;

public class Analyze_Errors implements PlugIn
{

	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "analyze interest point errors", "Analyze", true, true, true, true, true ) )
			return;

		final SpimData2 data = result.getData();
		final List< ViewId > viewIds =
				SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final HashMap<String, Double > labelAndWeights = getParameters(data, viewIds);
	}

	public static void getErrors(
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		
	}

	public static HashMap<String, Double > getParameters(
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		final GenericDialog gd = new GenericDialog( "Analyze Interest Point Errors" );

		// check which channels and labels are available and build the choices
		final String[] labelsRaw = InterestPointTools.getAllInterestPointLabels( data, viewIds );

		if ( labelsRaw.length == 0 )
		{
			IOFunctions.printErr( "No interest points available, stopping. Please run Interest Point Detection first" );
			return null;
		}

		final String[] labels = new String[ labelsRaw.length + 1 ];
		for ( int i = 0; i < labelsRaw.length; ++i )
			labels[ i ] = labelsRaw[ i ];
		labels[ labelsRaw.length ] = "Select multiple interestpoints [extra dialog]";

		// choose the first label that is complete if possible
		if ( Interest_Point_Registration.defaultLabel < 0 || Interest_Point_Registration.defaultLabel >= labels.length )
		{
			Interest_Point_Registration.defaultLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) && !labels[ i ].startsWith( "Select multiple interestpoints") )
				{
					Interest_Point_Registration.defaultLabel = i;
					break;
				}

			if ( Interest_Point_Registration.defaultLabel == -1 )
				Interest_Point_Registration.defaultLabel = 0;
		}

		System.out.println( Interest_Point_Registration.defaultLabel );

		gd.addChoice( "Interest_points" , labels, labels[ Interest_Point_Registration.defaultLabel ] );
		//gd.addCheckbox( "Print sorted results", true );
		//gd.addCheckbox( "Select & color-code worst pair in GUI", true );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		// assemble which label has been selected
		final int labelChoice = Interest_Point_Registration.defaultLabel = gd.getNextChoiceIndex();
		//final boolean print = gd.getNextBoolean();
		//final boolean select = gd.getNextBoolean();

		final HashMap<String, Double > labelAndWeight = new HashMap<>();

		if ( labelChoice < labels.length - 1 )
		{
			labelAndWeight.put( InterestPointTools.getSelectedLabel( labels, labelChoice ), 1.0 );
		}
		else
		{
			final ArrayList< String > labelChoices = Interest_Point_Registration.multipleInterestPointsGUI( labels );

			final GenericDialog gdLabel2 = new GenericDialog( "Select error weights" );

			if ( Interest_Point_Registration.defaultLabelWeights == null || Interest_Point_Registration.defaultLabelWeights.length != labelChoices.size() )
			{
				Interest_Point_Registration.defaultLabelWeights = new double[ labelChoices.size() ];
				Arrays.setAll( Interest_Point_Registration.defaultLabelWeights, d -> 1.0 );
			}
			
			gdLabel2.addMessage( "Weights for interest point labels:", GUIHelper.largefont );

			for ( int i = 0; i < labelChoices.size(); ++i )
				gdLabel2.addNumericField( labelChoices.get( i ) + " w=", Interest_Point_Registration.defaultLabelWeights[ i ], 2 );

			gdLabel2.showDialog();
			if ( gdLabel2.wasCanceled() )
				return null;

			for ( int i = 0; i < labelChoices.size(); ++i )
			{
				labelAndWeight.put( labelChoices.get( i ), Interest_Point_Registration.defaultLabelWeights[ i ] = gdLabel2.getNextNumber() );
				IOFunctions.println( labelChoices.get( i ) + ", weight=" + labelAndWeight.get( labelChoices.get( i ) ) );
			}
		}

		return labelAndWeight;
	}

	public static void main( String[] args )
	{
		new ImageJ();

		if ( System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLURI = "/Users/preibischs/SparkTest/Stitching/dataset.xml";

		new Analyze_Errors().run( null );
	}
	
}
