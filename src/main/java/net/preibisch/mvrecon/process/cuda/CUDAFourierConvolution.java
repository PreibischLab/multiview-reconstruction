package net.preibisch.mvrecon.process.cuda;


public interface CUDAFourierConvolution extends CUDAStandardFunctions 
{
	/*
	__declspec(dllexport) imageType* convolution3DfftCUDA(imageType* im,int* imDim,imageType* kernel,int* kernelDim,int devCUDA);
	 */
	public float[] convolution3DfftCUDA( float[] im, int[] imDim, float[] kernel, int[] kernelDim, int devCUDA );
	public void convolution3DfftCUDAInPlace( float[] im, int[] imDim, float[] kernel, int[] kernelDim, int devCUDA );
}
