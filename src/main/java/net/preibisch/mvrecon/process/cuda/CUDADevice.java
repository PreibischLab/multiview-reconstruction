package net.preibisch.mvrecon.process.cuda;

public class CUDADevice implements Comparable< CUDADevice >
{
	final String name;
	final long totalMem, freeMem;
	final int id, majorComputeVersion, minorComputeVersion;

	public CUDADevice( final int id, final String name, final long totalMem, final long freeMem, final int majorComputeVersion, final int minorComputeVersion )
	{
		this.id = id;
		this.name = name;
		this.totalMem = totalMem;
		this.freeMem = freeMem;
		this.majorComputeVersion = majorComputeVersion;
		this.minorComputeVersion = minorComputeVersion;
	}

	public int getDeviceId() { return id; }
	public String getDeviceName() { return name; }
	public long getTotalDeviceMemory() { return totalMem; }
	public long getFreeDeviceMemory() { return freeMem; }
	public int getMajorComputeVersion() { return majorComputeVersion; }
	public int getMinorComputeVersion() { return minorComputeVersion; }
	public String getComputeVersion() { return getMajorComputeVersion() + "." + getMinorComputeVersion(); }

	@Override
	public String toString() { return 
			getDeviceName() + " (id=" + getDeviceId() + ", mem=" + getTotalDeviceMemory()/(1024*1024) + 
			"MB (" + getFreeDeviceMemory()/(1024*1024) + "MB free),"
			+ " CUDA capability " + getComputeVersion() + ")"; }

	@Override
	public int compareTo( final CUDADevice dev )
	{
		return getDeviceId() - dev.getDeviceId();
	}
}
