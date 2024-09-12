package net.preibisch.mvrecon.process.fusion.blk;

import java.util.List;

import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.type.numeric.real.FloatType;

class WeightedAverage
{
	public static BlockSupplier< FloatType > of(
			final List< BlockSupplier< FloatType > > images,
			final List< BlockSupplier< FloatType > > weights,
			final Overlap overlap)
	{
		return new WeightedAverageBlockSupplier( images, weights, overlap );
	}
}
