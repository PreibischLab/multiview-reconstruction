/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.pairwise;

import java.awt.Font;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.TransformationModelGUI;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.rgldm.RGLDMPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.rgldm.RGLDMParameters;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;

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
	public static float min_inlier_factor = 3f;

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
		gd.addCheckbox( "Limit_search_radius", RGLDMParameters.defaultLimitSearchRadius );
		gd.addSlider( "Search_radius", 10.0, 1000.0, RGLDMParameters.defaultSearchRadius );

		gd.addMessage( "" );
		gd.addMessage( "Parameters for robust model-based outlier removal (RANSAC)", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd.addMessage( "" );

		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 100.0, RANSACParameters.max_epsilon );
		gd.addSlider( "Inlier_factor (minimal amount of inliers)", 1, 20, min_inlier_factor );
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
		final boolean limitSearchRadius = RGLDMParameters.defaultLimitSearchRadius = gd.getNextBoolean();
		final double searchRadius = RGLDMParameters.defaultSearchRadius = gd.getNextNumber();
		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		final float inlierFactor = min_inlier_factor = (float)gd.getNextNumber();
		final int ransacIterations = RANSACParameters.ransacChoicesIterations[ defaultRANSACIterationChoice = gd.getNextChoiceIndex() ];

		final float minInlierRatio;
		if ( ratioOfDistance >= 2 )
			minInlierRatio = RANSACParameters.min_inlier_ratio;
		else if ( ratioOfDistance >= 1.5 )
			minInlierRatio = RANSACParameters.min_inlier_ratio / 10;
		else
			minInlierRatio = RANSACParameters.min_inlier_ratio / 100;

		this.parameters = new RGLDMParameters(
				model.getModel(),
				RGLDMParameters.differenceThreshold,
				ratioOfDistance,
				limitSearchRadius,
				searchRadius,
				numNeighbors,
				redundancy );
		this.ransacParams = new RANSACParameters( maxEpsilon, minInlierRatio, inlierFactor, ransacIterations );

		IOFunctions.println( "Selected Paramters:" );
		IOFunctions.println( "model: " + defaultModel );
		IOFunctions.println( "numNeighbors: " + numNeighbors );
		IOFunctions.println( "redundancy: " + redundancy );
		IOFunctions.println( "ratioOfDistance: " + ratioOfDistance );
		IOFunctions.println( "maxEpsilon: " + maxEpsilon );
		IOFunctions.println( "inlierFactor: " + inlierFactor );
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
