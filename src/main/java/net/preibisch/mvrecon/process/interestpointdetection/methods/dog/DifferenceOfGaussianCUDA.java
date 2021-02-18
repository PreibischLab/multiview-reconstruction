/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.interestpointdetection.methods.dog;

import java.util.List;

import mpicbg.imglib.algorithm.OutputAlgorithm;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.util.Util;
import mpicbg.imglib.wrapper.ImgLib2;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.process.cuda.Block;
import net.preibisch.mvrecon.process.cuda.BlockGenerator;
import net.preibisch.mvrecon.process.cuda.BlockGeneratorVariableSizePrecise;
import net.preibisch.mvrecon.process.cuda.BlockGeneratorVariableSizeSimple;
import net.preibisch.mvrecon.process.cuda.CUDADevice;
import net.preibisch.mvrecon.process.cuda.CUDASeparableConvolution;
import net.preibisch.mvrecon.process.cuda.CUDASeparableConvolutionFunctions;
import net.preibisch.mvrecon.process.cuda.CUDASeparableConvolutionFunctions.OutOfBounds;

public class DifferenceOfGaussianCUDA extends DifferenceOfGaussianNewPeakFinder
{
	final Img< net.imglib2.type.numeric.real.FloatType > img2;
	final List< CUDADevice > devList;
	final CUDASeparableConvolution cuda;
	final boolean accurate;
	final double percentGPUMem;
	
	final CUDADevice cudaDev1, cudaDev2;

	public DifferenceOfGaussianCUDA(
			final CUDASeparableConvolution cuda,
			final double percentGPUMem,
			final List< CUDADevice > devList,
			final Image< FloatType > img1,
			final Img< net.imglib2.type.numeric.real.FloatType > img2,
			final boolean accurate,
			double[] sigma1, double[] sigma2, double minPeakValue,
			double normalizationFactor)
	{
		super( img1, null, sigma1, sigma2, minPeakValue, normalizationFactor );

		this.img2 = img2;
		this.percentGPUMem = percentGPUMem;
		this.devList = devList;
		this.cuda = cuda;
		this.accurate = accurate;

		if ( devList.size() > 1 )
		{
			this.setComputeConvolutionsParalell( true );
			this.cudaDev1 = devList.get( 0 );
			this.cudaDev2 = devList.get( 1 );
		}
		else
		{
			this.setComputeConvolutionsParalell( false );
			this.cudaDev1 = this.cudaDev2 = devList.get( 0 );
		}
	}

	int countCUDA = 0;

	@Override
	protected OutputAlgorithm< FloatType > getGaussianConvolution( final double[] sigma, final int numThreads )
	{
		if ( countCUDA == 0 )
		{
			countCUDA = 1;
			return new CUDAOutput( img2, percentGPUMem, cudaDev1, cuda, accurate, sigma );
		}
		else
		{
			countCUDA = 0;
			return new CUDAOutput( img2, percentGPUMem, cudaDev2, cuda, accurate, sigma );
		}
	}

	public static class CUDAOutput implements OutputAlgorithm< FloatType >
	{
		final Img< net.imglib2.type.numeric.real.FloatType > img, result;
		final CUDADevice cudaDevice;
		final CUDASeparableConvolutionFunctions cudaconvolve;
		final boolean accurate;
		final double[] sigma;
		final double percentGPUMem;

		public CUDAOutput(
				final Img< net.imglib2.type.numeric.real.FloatType > img,
				final double percentGPUMem,
				final CUDADevice cudaDevice,
				final CUDASeparableConvolution cuda,
				final boolean accurate,
				final double[] sigma )
		{
			this.img = img;
			this.percentGPUMem = percentGPUMem;
			this.result = img.factory().create( img, new net.imglib2.type.numeric.real.FloatType() );
			this.cudaDevice = cudaDevice;
			this.accurate = accurate;
			this.sigma = sigma;

			this.cudaconvolve = new CUDASeparableConvolutionFunctions( cuda, cudaDevice.getDeviceId() );
		}

		@Override
		public boolean checkInput() { return true; }

		@Override
		public boolean process()
		{
			// do not operate at the edge, 80% of the memory is a good idea I think
			final long memAvail = Math.round( cudaDevice.getFreeDeviceMemory() * ( percentGPUMem / 100.0 ) );
			final long imgBytes = numPixels() * 4 * 2; // float, two images on the card at once

			final long[] numBlocksDim = net.imglib2.util.Util.int2long( computeNumBlocksDim( memAvail, imgBytes, percentGPUMem, img.numDimensions(), "CUDA-Device " + cudaDevice.getDeviceId() ) );
			final BlockGenerator< Block > generator;

			if ( accurate )
				generator = new BlockGeneratorVariableSizePrecise( numBlocksDim );
			else
				generator = new BlockGeneratorVariableSizeSimple( numBlocksDim );

			final List< Block > blocks = generator.divideIntoBlocks( getImgSize( img ), getKernelSize( sigma ) );

			if ( !accurate && blocks.size() == 1 && ArrayImg.class.isInstance( img ) )
			{
				IOFunctions.println( "Conovlving image as one single block." );
				long time = System.currentTimeMillis();

				// copy the only directly into the result
				blocks.get( 0 ).copyBlock( img, result );
				long copy = System.currentTimeMillis();
				IOFunctions.println( "Copying data took " + ( copy - time ) + "ms" );

				// convolve
				final float[] resultF = ((FloatArray)((ArrayImg< net.imglib2.type.numeric.real.FloatType, ? > )result).update( null ) ).getCurrentStorageArray();
				cudaconvolve.gauss( resultF, getImgSizeInt( result ), sigma, OutOfBounds.EXTEND_BORDER_PIXELS, 0 );
				IOFunctions.println( "Convolution took " + ( System.currentTimeMillis() - copy ) + "ms using device=" + cudaDevice.getDeviceName() + " (id=" + cudaDevice.getDeviceId() + ")" );

				// no copy back required
			}
			else
			{
				final RandomAccessible< net.imglib2.type.numeric.real.FloatType > input;
				
				if ( accurate )
					input = Views.extendMirrorSingle( img );
				else
					input = img;
				
				for( final Block block : blocks )
				{
					//long time = System.currentTimeMillis();
					final ArrayImg< net.imglib2.type.numeric.real.FloatType, FloatArray > imgBlock = ArrayImgs.floats( block.getBlockSize() );

					// copy the block
					block.copyBlock( input, imgBlock );
					//long copy = System.currentTimeMillis();
					//IOFunctions.println( "Copying block took " + ( copy - time ) + "ms" );

					// convolve
					final float[] imgBlockF = ((FloatArray)((ArrayImg< net.imglib2.type.numeric.real.FloatType, ? > )imgBlock).update( null ) ).getCurrentStorageArray();
					cudaconvolve.gauss( imgBlockF, getImgSizeInt( imgBlock ), sigma, OutOfBounds.EXTEND_BORDER_PIXELS, 0 );
					//long convolve = System.currentTimeMillis();
					//IOFunctions.println( "Convolution took " + ( convolve - copy ) + "ms using device=" + cudaDevice.getDeviceName() + " (id=" + cudaDevice.getDeviceId() + ")" );

					// no copy back required
					block.pasteBlock( result, imgBlock );
					//IOFunctions.println( "Pasting block took " + ( System.currentTimeMillis() - convolve ) + "ms" );
				}
			}

			return true;
		}

		@Override
		public String getErrorMessage() { return ""; }

		@Override
		public Image<FloatType> getResult() { return ImgLib2.wrapFloatToImgLib1( result ); }

		protected static long[] getKernelSize( final double[] sigma )
		{
			final long[] dim = new long[ sigma.length ];
			for ( int d = 0; d < sigma.length; ++d )
				dim[ d ] = Util.createGaussianKernel1DDouble( sigma[ d ], false ).length;
			return dim;
		}

		public static long[] getImgSize( final Interval img )
		{
			final long[] dim = new long[ img.numDimensions() ];
			for ( int d = 0; d < img.numDimensions(); ++d )
				dim[ d ] = img.dimension( d );
			return dim;
		}

		protected static int[] getKernelSizeInt( final double[] sigma )
		{
			final int[] dim = new int[ sigma.length ];
			for ( int d = 0; d < sigma.length; ++d )
				dim[ d ] = Util.createGaussianKernel1DDouble( sigma[ d ], false ).length;
			return dim;
		}

		public static int[] getImgSizeInt( final Interval img )
		{
			final int[] dim = new int[ img.numDimensions() ];
			for ( int d = 0; d < img.numDimensions(); ++d )
				dim[ d ] = (int)img.dimension( d );
			return dim;
		}

		public static int[] computeNumBlocksDim( final long memAvail, final long memReq, final double percentGPUMem, final int n, final String start )
		{
			final int numBlocks = (int)( memReq / memAvail + Math.min( 1, memReq % memAvail ) );
			final double blocksPerDim = Math.pow( numBlocks, 1 / n );

			final int[] numBlocksDim = new int[ n ];

			for ( int d = 0; d < numBlocksDim.length; ++d )
				numBlocksDim[ d ] = (int)Math.round( Math.floor( blocksPerDim ) ) + 1;

			int numBlocksCurrent;
			
			do
			{
				numBlocksCurrent = numBlocks( numBlocksDim );

				for ( int d = 0; d < numBlocksDim.length; ++d )
				{
					++numBlocksDim[ d ];
					reduceBlockNumbers( numBlocksDim, numBlocks );
				}
				
				
			}
			while ( numBlocks( numBlocksDim ) < numBlocksCurrent );

			if ( start != null )
			{
				String out =
						start + ", mem=" + memAvail / (1024*1024) + 
						"MB (" + Math.round( percentGPUMem / 100 ) + "%), required mem=" + memReq / (1024*1024) + "MB, need to split up into " + numBlocks + " blocks: ";
	
				for ( int d = 0; d < numBlocksDim.length; ++d )
				{
					out += numBlocksDim[ d ];
					if ( d != numBlocksDim.length - 1 )
						out += "x";
				}
	
				IOFunctions.println( out );
			}
			return numBlocksDim;
		}

		protected static void reduceBlockNumbers( final int[] numBlocksDim, final int numBlocks )
		{
			boolean reduced;

			do
			{
				reduced = false;

				for ( int d = numBlocksDim.length - 1; d >= 0 ; --d )
				{
					if ( numBlocksDim[ d ] > 1 )
					{
						--numBlocksDim[ d ];
	
						if ( numBlocks( numBlocksDim ) < numBlocks )
							++numBlocksDim[ d ];
						else
							reduced = true;
					}
				}
			}
			while ( reduced );
		}

		protected static int numBlocks( final int[] numBlocksDim )
		{
			int numBlocks = 1;

			for ( int d = 0; d < numBlocksDim.length; ++d )
				numBlocks *= numBlocksDim[ d ];

			return numBlocks;
		}

		protected long numPixels()
		{
			if ( accurate )
			{
				long size = 1;

				for ( int d = 0; d < img.numDimensions(); ++d )
					size *= img.dimension( d ) + Util.createGaussianKernel1DDouble( sigma[ d ], false ).length - 1;

				return size;
			}
			else
			{
				return img.size();
			}
		}
	}
	
	public static void main( String[] args )
	{
		for ( int i = 1; i < 20; ++i )
		CUDAOutput.computeNumBlocksDim( 1024l * 1024l*1024l, i * 1000l * 1024l*1024l, 80, 3, "" );
	}
}
