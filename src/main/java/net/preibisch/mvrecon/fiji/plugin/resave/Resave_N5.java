package net.preibisch.mvrecon.fiji.plugin.resave;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProposeMipmaps;
import bdv.export.n5.BdvN5Names;
import bdv.export.n5.ExportScalePyramid;
import bdv.export.n5.WriteSequenceToN5;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.n5.N5ImgLoader;

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

		resaveN5( xml.getData(), vidsToProcess, n5File, xmlFile, new SimpleClusterResaveParameters() );
		
	}

	public void resaveN5(
			final SpimData2 data,
			final Collection<ViewId> vidsToResave,
			final File n5File,
			final File xmlFile,
			final SimpleClusterResaveParameters clusterParams
			)
	{
		final SpimData2 sdReduced = Resave_HDF5.reduceSpimData2( data, vidsToResave.stream().collect( Collectors.toList() ) );

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
						new ExportScalePyramid.DefaultLoopbackHeuristic(),
						null, //TODO: afterEachPlane,
						Runtime.getRuntime().availableProcessors(), // TODO: better numWorkers?
						null // TODO: add progress writer
						);
				
				if (clusterParams != null && clusterParams.finishedAttribute != null && !(clusterParams.finishedAttribute.equals( "" )) )
				{
					N5FSWriter n5 = new N5FSWriter( n5File.getAbsolutePath() );
					for (ViewId vid : vidsToResave)
						n5.setAttribute( BdvN5Names.getPathName( vid.getViewSetupId(), vid.getTimePointId() ), clusterParams.finishedAttribute, true);
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

	}

}
