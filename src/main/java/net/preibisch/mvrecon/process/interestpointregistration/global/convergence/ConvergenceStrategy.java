package net.preibisch.mvrecon.process.interestpointregistration.global.convergence;

public class ConvergenceStrategy
{
	double maxAllowedError;
	int maxIterations;
	int maxPlateauwidth;

	public ConvergenceStrategy( final double maxAllowedError )
	{
		this( maxAllowedError, 10000, 200 );
	}

	public ConvergenceStrategy(
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth )
	{
		this.maxAllowedError = maxAllowedError;
		this.maxIterations = maxIterations;
		this.maxPlateauwidth = maxPlateauwidth;
	}

	public double getMaxError() { return maxAllowedError; }
	public int getMaxIterations() { return maxIterations; }
	public int getMaxPlateauWidth() { return maxPlateauwidth; }
}
