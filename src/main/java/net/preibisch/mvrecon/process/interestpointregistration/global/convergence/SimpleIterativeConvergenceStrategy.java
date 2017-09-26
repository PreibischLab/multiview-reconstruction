package net.preibisch.mvrecon.process.interestpointregistration.global.convergence;

import mpicbg.models.TileConfiguration;

public class SimpleIterativeConvergenceStrategy extends IterativeConvergenceStrategy
{
	public static double minMaxError = 0.75; // three-quarters of a pixel, ok.

	final double relativeThreshold;
	final double absoluteThreshold;

	public SimpleIterativeConvergenceStrategy(
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth,
			final double relativeThreshold,
			final double absoluteThreshold )
	{
		super( maxAllowedError, maxIterations, maxPlateauwidth );

		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
	}

	public SimpleIterativeConvergenceStrategy(
			final double maxAllowedError,
			final double relativeThreshold,
			final double absoluteThreshold )
	{
		super( maxAllowedError );

		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
	}

	@Override
	public boolean isConverged( TileConfiguration tc )
	{
		double avgErr = tc.getError();
		double maxErr = tc.getMaxError();

		// the minMaxError makes sure that no links are dropped if the maximal error is already below a pixel
		if ( ( ( avgErr*relativeThreshold < maxErr && maxErr > minMaxError ) || avgErr > absoluteThreshold ) )
			return false;
		else
			return true;
	}
}
