package net.preibisch.mvrecon.process.resave;

import java.io.File;

public class HDF5Parameters extends MultiResolutionParameters
{
	File seqFile;
	File hdf5File;
	boolean deflate;
	boolean split;
	int timepointsPerPartition;
	int setupsPerPartition;
	boolean onlyRunSingleJob;
	int jobId;

	int convertChoice = 1;
	double min = Double.NaN;
	double max = Double.NaN;

	public HDF5Parameters(
			final boolean setMipmapManual, final int[][] resolutions, final int[][] subdivisions,
			final File seqFile, final File hdf5File,
			final boolean deflate,
			final boolean split, final int timepointsPerPartition, final int setupsPerPartition,
			final boolean onlyRunSingleJob, final int jobId,
			final int convertChoice, final double min, final double max )
	{
		super( setMipmapManual, resolutions, subdivisions );

		this.seqFile = seqFile;
		this.hdf5File = hdf5File;
		this.deflate = deflate;
		this.split = split;
		this.timepointsPerPartition = timepointsPerPartition;
		this.setupsPerPartition = setupsPerPartition;
		this.onlyRunSingleJob = onlyRunSingleJob;
		this.jobId = jobId;

		this.convertChoice = convertChoice;
		this.min = min;
		this.max = max;
	}

	public void setSeqFile( final File seqFile ) { this.seqFile = seqFile; }
	public void setHDF5File( final File hdf5File ) { this.hdf5File = hdf5File; }
	public void setDeflate( final boolean deflate ) { this.deflate = deflate; }
	public void setSplit( final boolean split ) { this.split = split; }
	public void setTimepointsPerPartition( final int timepointsPerPartition ) { this.timepointsPerPartition = timepointsPerPartition; }
	public void setSetupsPerPartition( final int setupsPerPartition ) { this.setupsPerPartition = setupsPerPartition; }
	public void setMin( final double min ) { this.min = min; }
	public void setMax( final double max ) { this.max = max; }

	public File getSeqFile() { return seqFile; }
	public File getHDF5File() { return hdf5File; }
	public boolean getDeflate() { return deflate; }
	public boolean getSplit() { return split; }
	public int getTimepointsPerPartition() { return timepointsPerPartition; }
	public int getSetupsPerPartition() { return setupsPerPartition; }

	public int getJobId() { return jobId; }
	public boolean onlyRunSingleJob() { return onlyRunSingleJob; }

	public int getConvertChoice() { return convertChoice; }
	public double getMin() { return min; }
	public double getMax() { return max; }
}
