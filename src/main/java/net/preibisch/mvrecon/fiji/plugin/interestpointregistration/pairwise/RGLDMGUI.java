package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.pairwise;

import java.awt.Font;

import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.TransformationModelGUI;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.rgldm.RGLDMPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.rgldm.RGLDMParameters;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

/**
 * Redundant Geometric Local Descriptor Matching (RGLDM)
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RGLDMGUI extends PairwiseGUI
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	public static int defaultRANSACIterationChoice = 1;
	protected TransformationModelGUI model = null;

	protected RGLDMParameters parameters;
	protected RANSACParameters ransacParams;

	@Override
	public RGLDMPairwise< InterestPoint > pairwiseMatchingInstance()
	{
		return new RGLDMPairwise< InterestPoint >( ransacParams, parameters );
	}

	@Override
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance()
	{
		return new RGLDMPairwise< GroupedInterestPoint< ViewId > >( ransacParams, parameters );
	}

	@Override
	public RGLDMGUI newInstance() { return new RGLDMGUI(); }

	@Override
	public String getDescription() { return "Precise descriptor-based (translation invariant)";}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		if ( presetModel == null )
		{
			gd.addChoice( "Transformation model", TransformationModelGUI.modelChoice, TransformationModelGUI.modelChoice[ defaultModel ] );
			gd.addCheckbox( "Regularize_model", defaultRegularize );
		}

		gd.addSlider( "Number_of_neighbors for the descriptors", 1, 10, RGLDMParameters.numNeighbors );
		gd.addSlider( "Redundancy for descriptor matching", 0, 10, RGLDMParameters.redundancy );
		gd.addSlider( "Significance required for a descriptor match", 1.0, 10.0, RGLDMParameters.ratioOfDistance );

		gd.addMessage( "" );
		gd.addMessage( "Parameters for robust model-based outlier removal (RANSAC)", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd.addMessage( "" );

		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 100.0, RANSACParameters.max_epsilon );
		gd.addChoice( "RANSAC_iterations", RANSACParameters.ransacChoices, RANSACParameters.ransacChoices[ defaultRANSACIterationChoice ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		if ( presetModel == null )
		{
			model = new TransformationModelGUI( defaultModel = gd.getNextChoiceIndex() );

			if ( defaultRegularize = gd.getNextBoolean() )
			{
				if ( !model.queryRegularizedModel() )
					return false;
			}
		}
		else
		{
			model = presetModel;
		}
	
		final int numNeighbors = RGLDMParameters.numNeighbors = (int)Math.round( gd.getNextNumber() );
		final int redundancy = RGLDMParameters.redundancy = (int)Math.round( gd.getNextNumber() );
		final float ratioOfDistance = RGLDMParameters.ratioOfDistance = (float)gd.getNextNumber();
		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		final int ransacIterations = RANSACParameters.ransacChoicesIterations[ defaultRANSACIterationChoice = gd.getNextChoiceIndex() ];

		final float minInlierRatio;
		if ( ratioOfDistance >= 2 )
			minInlierRatio = RANSACParameters.min_inlier_ratio;
		else if ( ratioOfDistance >= 1.5 )
			minInlierRatio = RANSACParameters.min_inlier_ratio / 10;
		else
			minInlierRatio = RANSACParameters.min_inlier_ratio / 100;

		this.parameters = new RGLDMParameters( model.getModel(), RGLDMParameters.differenceThreshold, ratioOfDistance, numNeighbors, redundancy );
		this.ransacParams = new RANSACParameters( maxEpsilon, minInlierRatio, RANSACParameters.min_inlier_factor, ransacIterations );

		IOFunctions.println( "Selected Paramters:" );
		IOFunctions.println( "model: " + defaultModel );
		IOFunctions.println( "numNeighbors: " + numNeighbors );
		IOFunctions.println( "redundancy: " + redundancy );
		IOFunctions.println( "ratioOfDistance: " + ratioOfDistance );
		IOFunctions.println( "maxEpsilon: " + maxEpsilon );
		IOFunctions.println( "ransacIterations: " + ransacIterations );
		IOFunctions.println( "minInlierRatio: " + minInlierRatio );

		return true;
	}

	@Override
	public TransformationModelGUI getMatchingModel() { return model; }

	@Override
	public double getMaxError() { return ransacParams.getMaxEpsilon(); }

	@Override
	public double globalOptError() { return ransacParams.getMaxEpsilon(); }
}
