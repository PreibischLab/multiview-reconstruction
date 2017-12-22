/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.deconvolution.iteration.mul;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.cuda.Block;
import net.preibisch.mvrecon.process.deconvolution.iteration.ComputeBlockThread.IterationStatistics;
import net.preibisch.mvrecon.process.deconvolution.DeconView;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.deconvolution.iteration.PsiInitialization;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class MultiViewDeconvolutionMul
{
	final public static float outsideValueImg = 0f; // the value the input image has if there is no data at this pixel
	final public static float minValueImg = 1f; // mininal value for the input image (as it is not normalized)
	final public static float minValue = 0.0001f; // minimal value for the deconvolved image

	public static int defaultBlendingRange = 12;
	public static int defaultBlendingBorder = -8;
	public static int cellDim = 32;
	public static int maxCacheSize = 10000;

	// for additional smoothing of weights in areas where many views contribute less than 100%
	public static float maxDiffRange = 0.1f;
	public static float scalingRange = 0.05f;
	public static boolean additionalSmoothBlending = false;

	// current iteration
	int it = 0;

	// the multi-view deconvolved image
	final Img< FloatType > psi;

	// the input data
	final DeconViews views;

	// the thread that will compute the iteration for each block independently
	final ComputeBlockMulThreadFactory computeBlockFactory;

	// the actual block compute threads
	final ArrayList< ComputeBlockMulThread > computeBlockThreads;

	// max intensities for each contributing view, ordered as in views
	final float[] max;

	final int numIterations;
	final double avgMax;

	boolean debug = false;
	int debugInterval = 1;

	// for debug
	ImageStack stack;
	CompositeImage ci;

	public MultiViewDeconvolutionMul(
			final DeconViews views,
			final int numIterations,
			final PsiInitialization psiInit,
			final ComputeBlockMulThreadFactory computeBlockFactory,
			final ImgFactory< FloatType > psiFactory )
	{
		this.computeBlockFactory = computeBlockFactory;
		this.views = views;
		this.numIterations = numIterations;

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Deconvolved image factory: " + psiFactory.getClass().getSimpleName() );

		this.psi = psiFactory.create( views.getPSIDimensions(), new FloatType() );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up " + computeBlockFactory.numParallelBlocks() + " Block Thread(s), using '" + computeBlockFactory.getClass().getSimpleName() + "'" );

		this.computeBlockThreads = new ArrayList<>();

		for ( int i = 0; i < computeBlockFactory.numParallelBlocks(); ++i )
			computeBlockThreads.add( computeBlockFactory.create( i ) );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Inititalizing PSI image using '" + psiInit.getClass().getSimpleName() + "'" );

		if ( !psiInit.runInitialization( psi, views.getViews(), views.getExecutorService() ) )
		{
			this.max = null;
			this.avgMax = 0;
		}
		else
		{
			this.max = psiInit.getMax();
	
			double avgMaxIntensity = 0;
			for ( int i = 0; i < max.length; ++i )
			{
				avgMaxIntensity += max[ i ];
				IOFunctions.println( "Max intensity in overlapping area of view " + i + ": " + max[ i ] );
			}
			this.avgMax = avgMaxIntensity / (double)max.length;
		}
	}

	public boolean initWasSuccessful() { return max != null; }
	public Img< FloatType > getPSI() { return psi; }
	public CompositeImage getDebugImage() { return ci; }
	public void setDebug( final boolean debug ) { this.debug = debug; }
	public void setDebugInterval( final int debugInterval ) { this.debugInterval = debugInterval; }

	public void runIterations()
	{
		if ( this.max == null )
			return;

		// run the deconvolution
		while ( it < numIterations )
		{
			// show the fused image first
			if ( debug && ( it-1 ) % debugInterval == 0 )
			{
				// if it is slices, wrap & copy otherwise virtual & copy - never use the actual image
				// as it is being updated in the process
				final ImagePlus tmp = DisplayImage.getImagePlusInstance( psi, true, "Psi", 0, avgMax ).duplicate();

				if ( this.stack == null )
				{
					this.stack = tmp.getImageStack();
					for ( int i = 0; i < this.psi.dimension( 2 ); ++i )
						this.stack.setSliceLabel( "Iteration 1", i + 1 );

					tmp.setTitle( "debug view" );
					this.ci = new CompositeImage( tmp, CompositeImage.COMPOSITE );
					this.ci.setDimensions( 1, (int)this.psi.dimension( 2 ), 1 );
					this.ci.setDisplayMode( IJ.GRAYSCALE );
					this.ci.show();
				}
				else if ( stack.getSize() == this.psi.dimension( 2 ) )
				{
					final ImageStack t = tmp.getImageStack();
					for ( int i = 0; i < this.psi.dimension( 2 ); ++i )
						this.stack.addSlice( "Iteration 2", t.getProcessor( i + 1 ) );
					this.ci.hide();

					this.ci = new CompositeImage( new ImagePlus( "debug view", this.stack ), CompositeImage.COMPOSITE );
					this.ci.setDimensions( 1, (int)this.psi.dimension( 2 ), 2 );
					this.ci.setDisplayMode( IJ.GRAYSCALE );
					this.ci.show();
				}
				else
				{
					final ImageStack t = tmp.getImageStack();
					for ( int i = 0; i < this.psi.dimension( 2 ); ++i )
						this.stack.addSlice( "Iteration " + i, t.getProcessor( i + 1 ) );

					this.ci.setStack( this.stack, 1, (int)this.psi.dimension( 2 ), stack.getSize() / (int)this.psi.dimension( 2 ) );
				}
			}

			runNextIteration();
		}

		// TODO: IOFunctions.println( "Masking never updated pixels." );
		// maskNeverUpdatedPixels( tmp1, views.getViews() );

		IOFunctions.println( "DONE (" + new Date(System.currentTimeMillis()) + ")." );
	}

	public void runNextIteration()
	{
		if ( this.max == null )
			return;

		++it;

		IOFunctions.println( "iteration: " + it + " (" + new Date(System.currentTimeMillis()) + ")" );

		final int totalNumBlocks = views.getViews().get( 0 ).getNumBlocks();
		final List< List< Block > > blocks = views.getViews().get( 0 ).getNonInterferingBlocks();

		for ( final DeconView view : views.getViews() )
		{
			if ( view.getNumBlocks() != totalNumBlocks )
				throw new RuntimeException( "only a constant number of blocks is supported." );

			if ( view.getNonInterferingBlocks().size() != blocks.size() )
				throw new RuntimeException( "only a constant number of block batches is supported." );

			for ( int i = 0; i < blocks.size(); ++i )
			{
				if ( blocks.get( i ).size() != view.getNonInterferingBlocks().get( i ).size() )
					throw new RuntimeException( "only a constant number of blocks within batches is supported." );

				for ( int j = 0; j < blocks.get( i ).size(); ++j )
				{
					final Block blockA = blocks.get( i ).get( j );
					final Block blockB = view.getNonInterferingBlocks().get( i ).get( j );

					for ( int d = 0; d < blockA.numDimensions(); ++d )
					{
						if (
								blockA.getBlockSize()[ d ] != blockB.getBlockSize()[ d ] ||
								blockA.getEffectiveSize()[ d ] != blockB.getEffectiveSize()[ d ] ||
								blockA.min( d ) != blockB.min( d ) ||
								blockA.max( d ) != blockB.max( d ) )
							throw new RuntimeException( "Block dimensions/offset/effective sizes not compatible, stopping." );
					}
					
				}
			}
		}

		final Vector< IterationStatistics > stats = new Vector<>();

		int currentTotalBlock = 0;

		// keep thelast blocks to be written back to the global psi image once it is not overlapping anymore
		final Vector< Pair< Pair< Integer, Block >, Img< FloatType > > > previousBlockWritebackQueue = new Vector<>();
		final Vector< Pair< Pair< Integer, Block >, Img< FloatType > > > currentBlockWritebackQueue = new Vector<>();

		int batch = 0;
		for ( final List< Block > blocksBatch : blocks )
		{
			final int numBlocksBefore = currentTotalBlock;
			final int numBlocksBatch = blocksBatch.size();
			currentTotalBlock += numBlocksBatch;

			System.out.println( "Processing " + numBlocksBatch + " blocks from batch " + (++batch) + "/" + blocks.size() );

			final AtomicInteger ai = new AtomicInteger();
			final Thread[] threads = new Thread[ computeBlockThreads.size() ];

			for ( int t = 0; t < computeBlockThreads.size(); ++t )
			{
				final int threadId = t;

				threads[ threadId ] = new Thread( new Runnable()
				{
					public void run()
					{
						// one ComputeBlockThread creates a temporary image for I/O, valid throughout the whole cycle
						final ComputeBlockMulThread blockThread = computeBlockThreads.get( threadId );

						int blockId;

						while ( ( blockId = ai.getAndIncrement() ) < numBlocksBatch )
						{
							final int blockIdOut = blockId + numBlocksBefore;

							final Block blockStruct = blocksBatch.get( blockId );
							System.out.println( " block " + blockIdOut + ", " + Util.printInterval( blockStruct ) );

							long time = System.currentTimeMillis();
							blockStruct.copyBlock( Views.extendMirrorSingle( psi ), blockThread.getPsiBlockTmp() );
							System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): copy " + (System.currentTimeMillis() - time) );

							final List< DeconView > view = new ArrayList<>();
							final List< RandomAccessibleInterval< FloatType > > imgBlock = new ArrayList<>();
							final List< RandomAccessibleInterval< FloatType > > weightBlock = new ArrayList<>();
							final List< Float > maxIntensityView = new ArrayList<>();
							final List< ArrayImg< FloatType, ? > > kernel1 = new ArrayList<>();
							final List< ArrayImg< FloatType, ? > > kernel2 = new ArrayList<>();

							for ( int i = 0; i < views.getViews().size(); ++i )
							{
								view.add( views.getViews().get( i ) );
								imgBlock.add( Views.zeroMin( Views.interval( Views.extendZero( views.getViews().get( i ).getImage() ), blockStruct ) ) );
								weightBlock.add( Views.zeroMin( Views.interval( Views.extendZero( views.getViews().get( i ).getWeight() ), blockStruct ) ) );
								maxIntensityView.add( max[ i ] );
								kernel1.add( views.getViews().get( i ).getPSF().getKernel1() );
								kernel2.add( views.getViews().get( i ).getPSF().getKernel2() );
							}

							time = System.currentTimeMillis();
							stats.add( blockThread.runIteration(
									view,
									imgBlock,//imgBlock,
									weightBlock,//weightBlock,
									maxIntensityView,
									kernel1,
									kernel2 ) );
							System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): compute " + (System.currentTimeMillis() - time) );

							time = System.currentTimeMillis();
							if ( totalNumBlocks == 1 )
							{
								blockStruct.pasteBlock( psi, blockThread.getPsiBlockTmp() );
								System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): paste " + (System.currentTimeMillis() - time) );
							}
							else
							{
								// copy to the writequeue
								final Img< FloatType > tmp = blockThread.getPsiBlockTmp().factory().create( blockThread.getPsiBlockTmp(), new FloatType() );
								FusionTools.copyImg( blockThread.getPsiBlockTmp(), tmp, false, views.getExecutorService() );
								currentBlockWritebackQueue.add( new ValuePair<>( new ValuePair<>( blockIdOut, blockStruct ), tmp ) );

								System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): saving for later pasting " + (System.currentTimeMillis() - time) );
							}
						}
					}
				});
			}

			// run the threads that process all blocks of this batch in parallel (often, this will be just one thread)
			FusionTools.runThreads( threads );

			// write back previous list of blocks
			writeBack( psi, previousBlockWritebackQueue );

			previousBlockWritebackQueue.clear();
			previousBlockWritebackQueue.addAll( currentBlockWritebackQueue );
			currentBlockWritebackQueue.clear();

		} // finish one block batch

		// write back last list of blocks
		writeBack( psi, previousBlockWritebackQueue );

		// accumulate the results from the individual blocks
		final IterationStatistics is = new IterationStatistics();

		for ( int i = 0; i < stats.size(); ++i )
		{
			is.sumChange += stats.get( i ).sumChange;
			is.maxChange = Math.max( is.maxChange, stats.get( i ).maxChange );
		}

		IOFunctions.println( "iteration: " + it + " --- sum change: " + is.sumChange + " --- max change per pixel: " + is.maxChange );

	}

	protected static final void writeBack( final Img< FloatType > psi, final Vector< Pair< Pair< Integer, Block >, Img< FloatType > > > blockWritebackQueue )
	{
		for ( final Pair< Pair< Integer, Block >, Img< FloatType > > writeBackBlock : blockWritebackQueue )
		{
			long time = System.currentTimeMillis();
			writeBackBlock.getA().getB().pasteBlock( psi, writeBackBlock.getB() );
			System.out.println( " block " + writeBackBlock.getA().getA() + ", (CPU): paste " + (System.currentTimeMillis() - time) );
		}
	}
}
