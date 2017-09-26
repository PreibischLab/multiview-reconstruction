package net.preibisch.mvrecon.process.interestpointregistration.global.convergence;

import mpicbg.models.TileConfiguration;

public abstract class IterativeConvergenceStrategy extends ConvergenceStrategy
{
	public IterativeConvergenceStrategy( 
			final double maxAllowedError,
			final int maxIterations,
			final int maxPlateauwidth )
	{
		super( maxAllowedError, maxIterations, maxPlateauwidth );
	}

	public IterativeConvergenceStrategy( final double maxAllowedError )
	{
		super( maxAllowedError );
	}

	public abstract boolean isConverged( final TileConfiguration tc );
}
