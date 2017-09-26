package net.preibisch.mvrecon.process.deconvolution.iteration;

import java.util.concurrent.ExecutorService;

import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.process.deconvolution.MultiViewDeconvolution;

public class ComputeBlockThreadCPUFactory implements ComputeBlockThreadFactory
{
	final ExecutorService service;
	final float minValue;
	final float lambda;
	final int[] blockSize;
	final ImgFactory< FloatType > blockFactory;

	public ComputeBlockThreadCPUFactory(
			final ExecutorService service,
			final float minValue,
			final float lambda,
			final int[] blockSize,
			final ImgFactory< FloatType > blockFactory )
	{
		this.service = service;
		this.minValue = minValue;
		this.lambda = lambda;
		this.blockSize = blockSize.clone();
		this.blockFactory = blockFactory;
	}

	public ComputeBlockThreadCPUFactory(
			final ExecutorService service,
			final float lambda,
			final int[] blockSize,
			final ImgFactory< FloatType > blockFactory )
	{
		this.service = service;
		this.minValue = MultiViewDeconvolution.minValue;
		this.lambda = lambda;
		this.blockSize = blockSize.clone();
		this.blockFactory = blockFactory;
	}

	@Override
	public ComputeBlockThread create( final int id )
	{
		return new ComputeBlockThreadCPU( service, minValue, lambda, id, blockSize, blockFactory );
	}

	@Override
	public int numParallelBlocks() { return 1; }

	@Override
	public String toString()
	{
		return "CPU based";
	}
}
