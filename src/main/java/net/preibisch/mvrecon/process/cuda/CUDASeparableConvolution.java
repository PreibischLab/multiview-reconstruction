package net.preibisch.mvrecon.process.cuda;

/**
 * Interface to load the native library for separable convolution using CUDA/CPU
 * (https://github.com/StephanPreibisch/SeparableConvolutionCUDALib)
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public interface CUDASeparableConvolution extends CUDAStandardFunctions
{
	// In-place convolution with a maximal kernel diameter of 31
	// outofbounds: 0 == zero, 1 == value, 2 == extendlastpixel
	public boolean convolve_127( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int imageW, int imageH, int imageD, boolean convolveX, boolean convolveY, boolean convolveZ, int outofbounds, float outofboundsvalue, int devCUDA );
	public boolean convolve_63( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int imageW, int imageH, int imageD, boolean convolveX, boolean convolveY, boolean convolveZ, int outofbounds, float outofboundsvalue, int devCUDA );
	public boolean convolve_31( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int imageW, int imageH, int imageD, boolean convolveX, boolean convolveY, boolean convolveZ, int outofbounds, float outofboundsvalue, int devCUDA );
	public boolean convolve_15( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int imageW, int imageH, int imageD, boolean convolveX, boolean convolveY, boolean convolveZ, int outofbounds, float outofboundsvalue, int devCUDA );
	public boolean convolve_7( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int imageW, int imageH, int imageD, boolean convolveX, boolean convolveY, boolean convolveZ, int outofbounds, float outofboundsvalue, int devCUDA );

	// CPU implementation
	// outofbounds: 0 == zero, 1 == value, 2 == extendlastpixel
	public void convolutionCPU( float[] image, float[] kernelX, float[] kernelY, float[] kernelZ, int kernelRX, int kernelRY, int kernelRZ, int imageW, int imageH, int imageD, int outofbounds, float outofboundsvalue );
}
