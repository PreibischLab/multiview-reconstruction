package net.preibisch.mvrecon.fiji.plugin.resave;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.n5.WriteSequenceToN5;
import bdv.img.n5.BdvN5Format;
import fiji.util.gui.GenericDialogPlus;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_N5.SimpleClusterResaveParameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.n5.N5ImgLoader;
import net.preibisch.mvrecon.headless.resave.HeadlessParseQueryXML;

public class Resave_N5 implements PlugIn
{
	static class SimpleClusterResaveParameters
	{
		public boolean saveXML = true;
		public boolean saveData = true;
		public String finishedAttribute = "finishedResave";
	}

	@Override
	public void run(String arg)
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "Resaving as HDF5", "Resave", true, true, true, true, true ) )
			return;

		ArrayList<ViewId> vidsToProcess = new ArrayList<>();
		for (TimePoint tp : xml.getTimePointsToProcess())
			for (ViewSetup vs : xml.getViewSetupsToProcess())
				vidsToProcess.add( new ViewId( tp.getId(), vs.getId() ) );
		
		File xmlFile = new File(xml.getXMLFileName().replace( ".xml", "-n5.xml" ));
		File n5File = new File(xml.getXMLFileName().replace( ".xml", ".n5" ));

		final GenericDialogPlus gdp = new GenericDialogPlus( "Cluster Options" );
		gdp.addFileField( "Output_XML", xmlFile.getAbsolutePath() );
		gdp.addFileField( "Output_N5", n5File.getAbsolutePath() );
		gdp.addCheckbox( "Write_XML", true );
		gdp.addCheckbox( "Write_data", true );

		gdp.showDialog();
		if (gdp.wasCanceled())
			return;

		xmlFile = new File(gdp.getNextString());
		n5File = new File(gdp.getNextString());

		SimpleClusterResaveParameters clusterParams = new SimpleClusterResaveParameters();
		clusterParams.saveXML = gdp.getNextBoolean();
		clusterParams.saveData = gdp.getNextBoolean();

		resaveN5( xml.getData(), vidsToProcess, n5File, xmlFile, clusterParams );
	}

	public static void resaveN5(
			final SpimData2 data,
			final Collection<ViewId> vidsToResave,
			final File n5File,
			final File xmlFile,
			final SimpleClusterResaveParameters clusterParams
			)
	{
		final SpimData2 sdReduced = Resave_HDF5.reduceSpimData2( data, vidsToResave.stream().collect( Collectors.toList() ) );

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// re-save data always if we have no cluster parameters, otherwise only if we asked for resave
		if (clusterParams == null || clusterParams.saveData)
		{

			// propose downsampling
			final Map< Integer, ExportMipmapInfo > proposedMipmaps = ProposeMipmaps.proposeMipmaps( sdReduced.getSequenceDescription() );
			// crude overwrite of block size (should be bigger than for normal hdf5)
			proposedMipmaps.keySet().forEach( k -> {
				ExportMipmapInfo exportMipmapInfo = proposedMipmaps.get( k );
				for (int[] row : exportMipmapInfo.getSubdivisions())
					Arrays.fill( row, 64 );
			});

			try
			{
				WriteSequenceToN5.writeN5File(
						sdReduced.getSequenceDescription(),
						proposedMipmaps,
						new GzipCompression(), // TODO: make user-settable
						n5File,
						new bdv.export.ExportScalePyramid.DefaultLoopbackHeuristic(),
						null, //TODO: afterEachPlane,
						Runtime.getRuntime().availableProcessors(), // TODO: better numWorkers?
						progressWriter
						);
				
				if (clusterParams != null && clusterParams.finishedAttribute != null && !(clusterParams.finishedAttribute.equals( "" )) )
				{
					N5FSWriter n5 = new N5FSWriter( n5File.getAbsolutePath() );
					for (ViewId vid : vidsToResave)
						n5.setAttribute( BdvN5Format.getPathName( vid.getViewSetupId(), vid.getTimePointId() ), clusterParams.finishedAttribute, true);
				}
				
			}
			catch ( IOException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		sdReduced.getSequenceDescription().setImgLoader( new N5ImgLoader( n5File.getAbsolutePath(), sdReduced.getSequenceDescription() ) );
		sdReduced.setBasePath( xmlFile.getParentFile() );

		if (clusterParams == null || clusterParams.saveXML)
		{
			try
			{
				new XmlIoSpimData2("").save( sdReduced, xmlFile.getAbsolutePath() );
			}
			catch ( SpimDataException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		progressWriter.setProgress( 1.0 );
		progressWriter.out().println( "done" );
	}

	public static void main(String[] args)
	{
		final Arguments parsedArgs = new Arguments( args );

		// try to load SpimData
		final HeadlessParseQueryXML xml = new HeadlessParseQueryXML();
		xml.loadXML( parsedArgs.getInputXMLPath(), false );

		// make default paths from XML path if not provided
		final String outXML = parsedArgs.getOutputXMLPath() != null ? parsedArgs.getOutputXMLPath() : parsedArgs.getInputXMLPath().replace( ".xml", "-n5.xml" );
		final String outN5 = parsedArgs.getOutputContainerPath() != null ? parsedArgs.getOutputContainerPath() : parsedArgs.getInputXMLPath().replace( ".xml", ".n5" );

		// parse views to resave, or use all if not provided
		Collection<ViewId> vidsToResave = parsedArgs.getViewsToResave() != null ?
					parseViewIdString( parsedArgs.getViewsToResave() ) :
					xml.getData().getSequenceDescription().getViewDescriptions().keySet();

		SimpleClusterResaveParameters params = new SimpleClusterResaveParameters();
		params.saveData = !parsedArgs.noResaveData;
		params.saveXML = !parsedArgs.noResaveXML;

		resaveN5( xml.getData(), vidsToResave, new File(outN5), new File(outXML), params );
	}

	public static List<ViewId> parseViewIdString(String input)
	{
		final ArrayList<ViewId> result = new ArrayList<>();
		for (String vid : input.split( Pattern.quote( ")," )))
		{
			String[] vidPair = vid.replace( ")", "" ).replace( "(", "" ).split( "," );
			result.add( new ViewId( Integer.parseInt( vidPair[0] ), Integer.parseInt( vidPair[0] ) ) );
		}
		return result;
	}

	private static class Arguments implements Serializable
	{
		private static final long serialVersionUID = -1467734459169624759L;

		@Option(name = "-i", aliases = { "--inputXMLPath" }, required = true,
				usage = "Path to an input SpimData XML")
		private String inputXMLPath;

		@Option(name = "-x", aliases = { "--outputXMLPath" }, required = false,
				usage = "Path to an output SpimData XML")
		private String outputXMLPath;
		
		@Option(name = "-o", aliases = { "--outputContainerPath" }, required = false,
				usage = "Path to an N5 container to store the output datasets")
		private String outputContainerPath;

		@Option(name = "-v", aliases = { "--viewsToResave" }, required = false,
				usage = "Which views to resave (timepoint,viewid),(,)...")
		private String viewsToResave;

		@Option(name = "-nd", aliases = { "--noResaveData" }, required = false,
				usage = "skips actual saving of data, so just XML is generated")
		private boolean noResaveData;
		
		@Option(name = "-nx", aliases = { "--noResaveXML" }, required = false,
				usage = "skips actual saving of XML")
		private boolean noResaveXML;

		public Arguments( final String... args ) throws IllegalArgumentException
		{
			final CmdLineParser parser = new CmdLineParser( this );
			try
			{
				parser.parseArgument( args );
			}
			catch ( final CmdLineException e )
			{
				System.err.println( e.getMessage() );
				parser.printUsage( System.err );
				System.exit( 1 );
			}
		}

		public String getInputXMLPath()
		{
			return inputXMLPath;
		}

		public String getOutputXMLPath()
		{
			return outputXMLPath;
		}

		public String getOutputContainerPath()
		{
			return outputContainerPath;
		}

		public boolean isNoResaveData()
		{
			return noResaveData;
		}

		public boolean isNoResaveXML()
		{
			return noResaveXML;
		}

		public String getViewsToResave()
		{
			return viewsToResave;
		}

		
	}

}
