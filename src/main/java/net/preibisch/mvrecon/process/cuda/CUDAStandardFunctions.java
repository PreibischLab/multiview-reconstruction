package net.preibisch.mvrecon.process.cuda;

import com.sun.jna.Library;

public interface CUDAStandardFunctions extends Library
{
	/*
	__declspec(dllexport) int getCUDAcomputeCapabilityMinorVersion(int devCUDA);
	__declspec(dllexport) int getCUDAcomputeCapabilityMajorVersion(int devCUDA);
	__declspec(dllexport) int getNumDevicesCUDA();
	__declspec(dllexport) char* getNameDeviceCUDA(int devCUDA);
	__declspec(dllexport) long long int getMemDeviceCUDA(int devCUDA);
	long long int getFreeMemDeviceCUDA(int devCUDA)
	 */
	public int getCUDAcomputeCapabilityMinorVersion(int devCUDA);
	public int getCUDAcomputeCapabilityMajorVersion(int devCUDA);
	/**
	 * @return -1 if driver crashed, otherwise the number of CUDA devices, &lt;= 0
	 */
	public int getNumDevicesCUDA();
	public void getNameDeviceCUDA(int devCUDA, byte[] name);
	public long getMemDeviceCUDA(int devCUDA);
	public long getFreeMemDeviceCUDA(int devCUDA);
}
