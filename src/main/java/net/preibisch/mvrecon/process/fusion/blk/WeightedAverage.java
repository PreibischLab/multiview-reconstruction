package net.preibisch.mvrecon.process.fusion.blk;

import java.util.List;

import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.type.numeric.real.FloatType;

public class WeightedAverage
{
	public static BlockSupplier< FloatType > of( final List< BlockSupplier< FloatType > > images, final List< BlockSupplier< FloatType > > weights )
	{
		return new WeightedAverageBlockSupplier( images, weights );
	}
}
