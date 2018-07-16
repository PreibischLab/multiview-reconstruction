package net.preibisch.mvrecon.process.resave;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
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
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.plugin.Toggle_Cluster_Options;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;

public class HDF5Tools
{
	public static void writeHDF5( final AbstractSpimData< ? > spimData, final HDF5Parameters params, final ProgressWriter progressWriter )
	{
		Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = MultiResolutionTools.getPerSetupExportMipmapInfo( spimData, params );
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

	public static < T extends AbstractSpimData< A >, A extends AbstractSequenceDescription< ?, ?, ? super ImgLoader > > void writeXML(
			final T spimData,
			final XmlIoAbstractSpimData< A, T > io,
			final HDF5Parameters params,
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

	public static ArrayList< Partition > getPartitions( final AbstractSpimData< ? > spimData, final HDF5Parameters params )
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

	public static final String[] convertChoices = {
			"Use min/max of each image (might flicker over time)",
			"Use min/max of first image (might saturate intenities over time)",
			"Manually define min/max" };

	public static int defaultConvertChoice = 1;
	public static double defaultMin = 0, defaultMax = 5;

	static boolean lastSetMipmapManual = false;

	static String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";

	static String lastChunkSizes = "{16,16,16}, {16,16,16}, {16,16,16}";

	static boolean lastSplit = false;

	static int lastTimepointsPerPartition = 1;

	static int lastSetupsPerPartition = 0;

	static boolean lastDeflate = true;

	static int lastJobIndex = 0;

	public static String lastExportPath = "/Users/pietzsch/Desktop/spimrec2.xml";

	public static HDF5Parameters getParameters( final ExportMipmapInfo autoMipmapSettings, final boolean askForXMLPath, final boolean is16bit )
	{
		return getParameters( autoMipmapSettings, askForXMLPath, "Export for BigDataViewer", is16bit );
	}

	public static HDF5Parameters getParameters( final ExportMipmapInfo autoMipmapSettings, final boolean askForXMLPath, final String dialogTitle, final boolean is16bit )
	{
		final boolean displayClusterProcessing = Toggle_Cluster_Options.displayClusterProcessing;
		if ( displayClusterProcessing )
		{
			lastSplit = true;
			lastTimepointsPerPartition = 1;
			lastSetupsPerPartition = 0;
		}

		while ( true )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( dialogTitle );

			gd.addCheckbox( "manual_mipmap_setup", lastSetMipmapManual );
			final Checkbox cManualMipmap = ( Checkbox ) gd.getCheckboxes().lastElement();
			gd.addStringField( "Subsampling_factors", lastSubsampling, 25 );
			final TextField tfSubsampling = ( TextField ) gd.getStringFields().lastElement();
			gd.addStringField( "Hdf5_chunk_sizes", lastChunkSizes, 25 );
			final TextField tfChunkSizes = ( TextField ) gd.getStringFields().lastElement();

			gd.addMessage( "" );
			gd.addCheckbox( "split_hdf5", lastSplit );
			final Checkbox cSplit = ( Checkbox ) gd.getCheckboxes().lastElement();
			gd.addNumericField( "timepoints_per_partition", lastTimepointsPerPartition, 0, 25, "" );
			final TextField tfSplitTimepoints = ( TextField ) gd.getNumericFields().lastElement();
			gd.addNumericField( "setups_per_partition", lastSetupsPerPartition, 0, 25, "" );
			final TextField tfSplitSetups = ( TextField ) gd.getNumericFields().lastElement();
			if ( displayClusterProcessing )
			{
				gd.addNumericField( "run_only_job_number", lastJobIndex, 0, 25, "" );
			}

			gd.addMessage( "" );
			gd.addCheckbox( "use_deflate_compression", lastDeflate );


			if ( askForXMLPath )
			{
				gd.addMessage( "" );
				PluginHelper.addSaveAsFileField( gd, "Export_path", lastExportPath, 25 );
			}

			if ( !is16bit )
			{
				gd.addMessage( "" );
				gd.addMessage( "Currently, only 16-bit data is supported for HDF5. Please define how to convert to 16bit.", GUIHelper.mediumstatusfont );
				gd.addChoice( "Convert_32bit", convertChoices, convertChoices[ defaultConvertChoice ] );
			}

			final String autoSubsampling = ProposeMipmaps.getArrayString( autoMipmapSettings.getExportResolutions() );
			final String autoChunkSizes = ProposeMipmaps.getArrayString( autoMipmapSettings.getSubdivisions() );
			gd.addDialogListener( new DialogListener()
			{
				@Override
				public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
				{
					gd.getNextBoolean();
					gd.getNextString();
					gd.getNextString();
					gd.getNextBoolean();
					gd.getNextNumber();
					gd.getNextNumber();
					if ( displayClusterProcessing )
						gd.getNextNumber();
					gd.getNextBoolean();
					if ( askForXMLPath )
						gd.getNextString();
					if ( !is16bit )
						gd.getNextChoiceIndex();
					if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == cManualMipmap )
					{
						final boolean useManual = cManualMipmap.getState();
						tfSubsampling.setEnabled( useManual );
						tfChunkSizes.setEnabled( useManual );
						if ( !useManual )
						{
							tfSubsampling.setText( autoSubsampling );
							tfChunkSizes.setText( autoChunkSizes );
						}
					}
					else if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == cSplit )
					{
						final boolean split = cSplit.getState();
						tfSplitTimepoints.setEnabled( split );
						tfSplitSetups.setEnabled( split );
					}
					return true;
				}
			} );

			tfSubsampling.setEnabled( lastSetMipmapManual );
			tfChunkSizes.setEnabled( lastSetMipmapManual );
			if ( !lastSetMipmapManual )
			{
				tfSubsampling.setText( autoSubsampling );
				tfChunkSizes.setText( autoChunkSizes );
			}

			tfSplitTimepoints.setEnabled( lastSplit );
			tfSplitSetups.setEnabled( lastSplit );

			if ( displayClusterProcessing )
			{
				cSplit.setEnabled( false );
				tfSplitTimepoints.setEnabled( false );
				tfSplitSetups.setEnabled( false );
			}

			gd.showDialog();
			if ( gd.wasCanceled() )
				return null;

			lastSetMipmapManual = gd.getNextBoolean();
			lastSubsampling = gd.getNextString();
			lastChunkSizes = gd.getNextString();
			lastSplit = gd.getNextBoolean();
			lastTimepointsPerPartition = ( int ) gd.getNextNumber();
			lastSetupsPerPartition = ( int ) gd.getNextNumber();
			if ( displayClusterProcessing )
			{
				lastJobIndex = ( int ) gd.getNextNumber();
			}
			lastDeflate = gd.getNextBoolean();
			if ( askForXMLPath )
				lastExportPath = gd.getNextString();
			if ( !is16bit )
				defaultConvertChoice = gd.getNextChoiceIndex();

			// parse mipmap resolutions and cell sizes
			final int[][] resolutions = PluginHelper.parseResolutionsString( lastSubsampling );
			final int[][] subdivisions = PluginHelper.parseResolutionsString( lastChunkSizes );
			if ( resolutions.length == 0 )
			{
				IJ.showMessage( "Cannot parse subsampling factors " + lastSubsampling );
				continue;
			}
			if ( subdivisions.length == 0 )
			{
				IJ.showMessage( "Cannot parse hdf5 chunk sizes " + lastChunkSizes );
				continue;
			}
			else if ( resolutions.length != subdivisions.length )
			{
				IJ.showMessage( "subsampling factors and hdf5 chunk sizes must have the same number of elements" );
				continue;
			}

			final File seqFile, hdf5File;

			if ( askForXMLPath )
			{
				String seqFilename = lastExportPath;
				if ( !seqFilename.endsWith( ".xml" ) )
					seqFilename += ".xml";
				seqFile = new File( seqFilename );
				final File parent = seqFile.getParentFile();
				if ( parent == null || !parent.exists() || !parent.isDirectory() )
				{
					IJ.showMessage( "Invalid export filename " + seqFilename );
					continue;
				}
				final String hdf5Filename = seqFilename.substring( 0, seqFilename.length() - 4 ) + ".h5";
				hdf5File = new File( hdf5Filename );
			}
			else
			{
				seqFile = hdf5File = null;
			}

			if ( defaultConvertChoice == 2 )
			{
				if ( Double.isNaN( defaultMin ) )
					defaultMin = 0;

				if ( Double.isNaN( defaultMax ) )
					defaultMax = 5;

				final GenericDialog gdMinMax = new GenericDialog( "Define min/max" );

				gdMinMax.addNumericField( "Min_Intensity_for_16bit_conversion", defaultMin, 1 );
				gdMinMax.addNumericField( "Max_Intensity_for_16bit_conversion", defaultMax, 1 );
				gdMinMax.addMessage( "Note: the typical range for multiview deconvolution is [0 ... 10] & for fusion the same as the input intensities., ",GUIHelper.mediumstatusfont );

				gdMinMax.showDialog();

				if ( gdMinMax.wasCanceled() )
					return null;
	
				defaultMin = gdMinMax.getNextNumber();
				defaultMax = gdMinMax.getNextNumber();
			}
			else
			{
				defaultMin = defaultMax = Double.NaN;
			}

			return new HDF5Parameters(
					lastSetMipmapManual, resolutions, subdivisions, seqFile, hdf5File, lastDeflate, lastSplit,
					lastTimepointsPerPartition, lastSetupsPerPartition, displayClusterProcessing, lastJobIndex,
					defaultConvertChoice, defaultMin, defaultMax );
		}
	}
}
