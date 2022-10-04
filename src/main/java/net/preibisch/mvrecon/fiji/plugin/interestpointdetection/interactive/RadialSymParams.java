package net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive;

import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.DifferenceOfGaussianGUI;

public class RadialSymParams {

	public double sigma = DifferenceOfGaussianGUI.defaultSigma, threshold = DifferenceOfGaussianGUI.defaultThreshold;

	public boolean findMaxima = DifferenceOfGaussianGUI.defaultFindMax;
	public boolean findMinima = DifferenceOfGaussianGUI.defaultFindMin;
}
