package net.preibisch.mvrecon.fiji.plugin.resave;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.export.WriteSequenceToHdf5.DefaultLoopbackHeuristic;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import net.preibisch.mvrecon.Threads;

public class GenericResaveHDF5 {
	public static class Parameters
	{
		boolean setMipmapManual;
		int[][] resolutions;
		int[][] subdivisions;
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

		public Parameters(
				final boolean setMipmapManual, final int[][] resolutions, final int[][] subdivisions,
				final File seqFile, final File hdf5File,
				final boolean deflate,
				final boolean split, final int timepointsPerPartition, final int setupsPerPartition,
				final boolean onlyRunSingleJob, final int jobId,
				final int convertChoice, final double min, final double max )
		{
			this.setMipmapManual = setMipmapManual;
			this.resolutions = resolutions;
			this.subdivisions = subdivisions;
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
		public void setResolutions( final int[][] resolutions ) { this.resolutions = resolutions; }
		public void setSubdivisions( final int[][] subdivisions ) { this.subdivisions = subdivisions; }
		public void setMipmapManual( final boolean setMipmapManual ) { this.setMipmapManual = setMipmapManual; }
		public void setDeflate( final boolean deflate ) { this.deflate = deflate; }
		public void setSplit( final boolean split ) { this.split = split; }
		public void setTimepointsPerPartition( final int timepointsPerPartition ) { this.timepointsPerPartition = timepointsPerPartition; }
		public void setSetupsPerPartition( final int setupsPerPartition ) { this.setupsPerPartition = setupsPerPartition; }
		public void setMin( final double min ) { this.min = min; }
		public void setMax( final double max ) { this.max = max; }

		public File getSeqFile() { return seqFile; }
		public File getHDF5File() { return hdf5File; }
		public int[][] getResolutions() { return resolutions; }
		public int[][] getSubdivisions() { return subdivisions; }
		public boolean getMipmapManual() { return setMipmapManual; }
		public boolean getDeflate() { return deflate; }
		public boolean getSplit() { return split; }
		public int getTimepointsPerPartition() { return timepointsPerPartition; }
		public int getSetupsPerPartition() { return setupsPerPartition; }

		public int getConvertChoice() { return convertChoice; }
		public double getMin() { return min; }
		public double getMax() { return max; }
	}
	
	public static ArrayList< Partition > getPartitions( final AbstractSpimData< ? > spimData, final Parameters params )
	{
		final AbstractSequenceDescription< ?, ?, ? > seq = spimData.getSequenceDescription();
		if ( params.split )
		{
			final String xmlFilename = params.seqFile.getAbsolutePath();
			final String basename = xmlFilename.endsWith( ".xml" ) ? xmlFilename.substring( 0, xmlFilename.length() - 4 ) : xmlFilename;
			List< TimePoint > timepoints = seq.getTimePoints().getTimePointsOrdered();
			List< ? extends BasicViewSetup > setups = seq.getViewSetupsOrdered();
			return Partition.split( timepoints, setups, params.timepointsPerPartition, params.setupsPerPartition, basename );
		}
		else
			return null;
	}
	
	public static Map< Integer, ExportMipmapInfo > getPerSetupExportMipmapInfo( final AbstractSpimData< ? > spimData, final Parameters params )
	{
		if ( params.setMipmapManual )
		{
			final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
			final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( params.resolutions, params.subdivisions );
			for ( final BasicViewSetup setup : spimData.getSequenceDescription().getViewSetupsOrdered() )
				perSetupExportMipmapInfo.put( setup.getId(), mipmapInfo );
			return perSetupExportMipmapInfo;
		}
		else
			return ProposeMipmaps.proposeMipmaps( spimData.getSequenceDescription() );
	}
	
	public static < T extends AbstractSpimData< A >, A extends AbstractSequenceDescription< ?, ?, ? super ImgLoader > > void writeXML(
			final T spimData,
			final XmlIoAbstractSpimData< A, T > io,
			final Parameters params,
			final ProgressWriter progressWriter )
		throws SpimDataException
	{
		final A seq = spimData.getSequenceDescription();
		final ArrayList< Partition > partitions = getPartitions( spimData, params );
		final Hdf5ImageLoader hdf5Loader = new Hdf5ImageLoader( params.hdf5File, partitions, null, false );
		seq.setImgLoader( hdf5Loader );
		spimData.setBasePath( params.seqFile.getParentFile() );
		try
		{
			if ( !params.onlyRunSingleJob || params.jobId == 0 )
				io.save( spimData, params.seqFile.getAbsolutePath() );
			progressWriter.setProgress( 1.0 );
		}
		catch ( final Exception e )
		{
			progressWriter.err().println( "Failed to write xml file " + params.seqFile );
			e.printStackTrace( progressWriter.err() );
		}
		progressWriter.out().println( "done" );
	}
	
	public static void writeHDF5( final AbstractSpimData< ? > spimData, final Parameters params, final ProgressWriter progressWriter )
	{
		Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = getPerSetupExportMipmapInfo( spimData, params );
		final ArrayList< Partition > partitions = getPartitions( spimData, params );
		AbstractSequenceDescription< ?, ?, ? > seq = spimData.getSequenceDescription();
		if ( partitions != null )
		{
			for ( int i = 0; i < partitions.size(); ++i )
			{
				final Partition partition = partitions.get( i );
				final ProgressWriter p = new SubTaskProgressWriter( progressWriter, 0, 0.95 * i / partitions.size() );
				progressWriter.out().printf( "proccessing partition %d / %d\n", ( i + 1 ), partitions.size() );
				if ( !params.onlyRunSingleJob || params.jobId == i + 1 )
					WriteSequenceToHdf5.writeHdf5PartitionFile( seq, perSetupExportMipmapInfo, params.deflate, partition, new DefaultLoopbackHeuristic(), null, Threads.numThreads(), p );
			}
			if ( !params.onlyRunSingleJob || params.jobId == 0 )
				WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, partitions, params.hdf5File );
		}
		else
		{
			final ProgressWriter p = new SubTaskProgressWriter( progressWriter, 0, 0.95 );
			WriteSequenceToHdf5.writeHdf5File( seq, perSetupExportMipmapInfo, params.deflate, params.hdf5File, new DefaultLoopbackHeuristic(), null, Threads.numThreads(), p );
		}
	}
	
	
}
