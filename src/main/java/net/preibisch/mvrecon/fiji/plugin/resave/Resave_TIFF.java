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
package net.preibisch.mvrecon.fiji.plugin.resave;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bdv.export.ProgressWriter;
import fiji.util.gui.GenericDialogPlus;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.ValuePair;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.StackImgLoaderIJ;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.process.export.Save3dTIFF;

public class Resave_TIFF implements PlugIn
{
	public static String defaultPath = null;
	public static int defaultContainer = 1;
	public static boolean defaultCompress = false;

	public static void main( final String[] args )
	{
		new Resave_TIFF().run( null );
	}

	public static class Parameters
	{
		public ImgFactory< ? extends NativeType< ? > > imgFactory;
		public String xmlFile;
		public boolean compress;
		
		public boolean compress() { return compress; }
		public String getXMLFile() { return xmlFile; }
		public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return imgFactory; }
	}

	@Override
	public void run( final String arg0 )
	{
		final LoadParseQueryXML lpq = new LoadParseQueryXML();

		if ( !lpq.queryXML( "Resaving as TIFF", "Resave", true, true, true, true, true ) )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );
		
		final Parameters params = getParameters();
		
		if ( params == null )
			return;

		final SpimData2 data = lpq.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, lpq.getViewSetupsToProcess(), lpq.getTimePointsToProcess() );

		// write the TIFF's
		writeTIFF( data, viewIds, new File( params.xmlFile ).getParent(), params.compress, progressWriter );

		// write the XML
		try
		{
			final Pair< SpimData2, List< String > > result = createXMLObject( data, viewIds, params );
			progressWriter.setProgress( 0.95 );

			// write the XML
			lpq.getIO().save( result.getA(), new File( params.xmlFile ).getAbsolutePath() );

			// copy the interest points if they exist
			copyInterestPoints( data.getBasePath(), new File( params.xmlFile ).getParentFile(), result.getB() );
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.xmlFile + "'." );
			e.printStackTrace();
		}
		finally
		{
			progressWriter.setProgress( 1.00 );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.xmlFile + "'." );
		}
	}

	public static void copyInterestPoints( final File srcBase, final File destBase, final List< String > filesToCopy )
	{
		// test if source and target directory are identical, if so stop
		String from = srcBase.getAbsolutePath();
		String to = destBase.getAbsolutePath();
		
		from = from.replace( "/./", "/" );
		to = to.replace( "/./", "/" );
		
		if ( from.endsWith( "/." ) )
			from = from.substring( 0, from.length() - 2 );

		if ( to.endsWith( "/." ) )
			to = to.substring( 0, to.length() - 2 );

		if ( new File( from ).getAbsolutePath().equals( new File( to ).getAbsolutePath() ) )
			return;

		final File src = new File( srcBase, "interestpoints" );
		
		if ( src.exists() )
		{
			final File target = new File( destBase, "interestpoints" );

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Interestpoint directory exists. Copying '" + src + "' >>> '" + target + "'" );

			try
			{
				copyFolder( src, target, filesToCopy );
			}
			catch (IOException e)
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED to copying '" + src + "' >>> '" + target + "': " + e );
				e.printStackTrace();
			}
		}
	}

	public static Parameters getParameters()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Resave dataset as TIFF" );

		if ( defaultPath == null )
			defaultPath = LoadParseQueryXML.defaultXMLfilename;

		PluginHelper.addSaveAsFileField( gd, "Select new XML", defaultPath, 80 );
		//gd.addCheckbox( "Lossless compression of TIFF files (ZIP)", defaultCompress );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final Parameters params = new Parameters();

		params.xmlFile = gd.getNextString();

		if ( !params.xmlFile.endsWith( ".xml" ) )
			params.xmlFile += ".xml";

		defaultPath = LoadParseQueryXML.defaultXMLfilename = params.xmlFile;

		params.compress = false; //defaultCompress = gd.getNextBoolean();

		if ( defaultContainer == 0 )
			params.imgFactory = new ArrayImgFactory< FloatType >();
		else
			params.imgFactory = new CellImgFactory< FloatType >();

		return params;
	}

	public static void writeTIFF( final SpimData spimData, final List< ViewId > viewIds, final String path, final boolean compress, final ProgressWriter progressWriter )
	{
		if ( compress )
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving compressed TIFFS to directory '" + path + "'" );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving TIFFS to directory '" + path + "'" );
		
		final Save3dTIFF save = new Save3dTIFF( path, compress );
		
		final int numAngles = SpimData2.getAllAnglesSorted( spimData, viewIds ).size();
		final int numChannels = SpimData2.getAllChannelsSorted( spimData, viewIds ).size();
		final int numIlluminations = SpimData2.getAllIlluminationsSorted( spimData, viewIds ).size();
		final int numTimepoints =  SpimData2.getAllTimePointsSorted( spimData, viewIds ).size();
		final int numTiles =  SpimData2.getAllTilesSorted( spimData, viewIds ).size();

		int i = 0;

		for ( final ViewId viewId : viewIds )
		{
			i++;

			final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
					viewId.getTimePointId(), viewId.getViewSetupId() );

			if ( !viewDescription.isPresent() )
				continue;

			final RandomAccessibleInterval img = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId(), LOAD_COMPLETELY );

			String filename = "img";

			if ( numTimepoints > 1 )
				filename += "_TL" + viewId.getTimePointId();

			if ( numChannels > 1 )
				filename += "_Ch" + viewDescription.getViewSetup().getChannel().getName();

			if ( numIlluminations > 1 )
				filename += "_Ill" + viewDescription.getViewSetup().getIllumination().getName();

			if ( numAngles > 1 )
				filename += "_Angle" + viewDescription.getViewSetup().getAngle().getName();
			
			if ( numTiles > 1 )
				filename += "_Tile" + viewDescription.getViewSetup().getTile().getName();

			save.exportImage( img, filename );

			progressWriter.setProgress( ((i-1) / (double)viewIds.size()) * 95.00  );
		}
	}


	public static Pair< SpimData2, List< String > > createXMLObject( final SpimData2 spimData, final List< ViewId > viewIds, final Parameters params )
	{
		int layoutTP = 0, layoutChannels = 0, layoutIllum = 0, layoutAngles = 0, layoutTiles = 0;
		String filename = "img";

		final int numAngles = SpimData2.getAllAnglesSorted( spimData, viewIds ).size();
		final int numChannels = SpimData2.getAllChannelsSorted( spimData, viewIds ).size();
		final int numIlluminations = SpimData2.getAllIlluminationsSorted( spimData, viewIds ).size();
		final int numTimepoints =  SpimData2.getAllTimePointsSorted( spimData, viewIds ).size();
		final int numTiles =  SpimData2.getAllTilesSorted( spimData, viewIds ).size();

		if ( numTimepoints > 1 )
		{
			filename += "_TL{t}";
			layoutTP = 1;
		}
		
		if ( numChannels > 1 )
		{
			filename += "_Ch{c}";
			layoutChannels = 1;
		}
		
		if ( numIlluminations > 1 )
		{
			filename += "_Ill{i}";
			layoutIllum = 1;
		}
		
		if ( numAngles > 1 )
		{
			filename += "_Angle{a}";
			layoutAngles = 1;
		}
		
		if ( numTiles > 1 )
		{
			filename += "_Tile{x}";
			layoutTiles = 1;
		}

		filename += ".tif";

		if ( params.compress )
			filename += ".zip";

		// Re-assemble a new SpimData object containing the subset of viewsetups and timepoints selected
		final List< String > filesToCopy = new ArrayList< String >();
		final SpimData2 newSpimData = assemblePartialSpimData2( spimData, viewIds, new File( params.xmlFile ).getParentFile(), filesToCopy );

		final StackImgLoaderIJ imgLoader = new StackImgLoaderIJ(
				new File( params.xmlFile ).getParentFile(),
				filename, params.imgFactory,
				layoutTP, layoutChannels, layoutIllum, layoutAngles, layoutTiles, newSpimData.getSequenceDescription() );
		newSpimData.getSequenceDescription().setImgLoader( imgLoader );

		return new ValuePair< SpimData2, List< String > >( newSpimData, filesToCopy );
	}
	
	public static String listAllTimePoints( final List<TimePoint> timePointsToProcess )
	{
		String t = "" + timePointsToProcess.get( 0 ).getId();

		for ( int i = 1; i < timePointsToProcess.size(); ++i )
			t += ", " + timePointsToProcess.get( i ).getId();

		return t;
	}

	public static void copyFolder( final File src, final File dest, final List< String > filesToCopy ) throws IOException
	{
		if ( src.isDirectory() )
		{
			if( !dest.exists() )
				dest.mkdir();

			for ( final String file : src.list() )
				copyFolder( new File( src, file ), new File( dest, file ), filesToCopy );
		}
		else
		{
			boolean contains = false;
			
			for ( int i = 0; i < filesToCopy.size() && !contains; ++i )
				if ( src.getName().contains( filesToCopy.get( i ) ) )
					contains = true;
			
			if ( contains )
			{
				final InputStream in = new FileInputStream( src );
				final OutputStream out = new FileOutputStream( dest ); 

				final byte[] buffer = new byte[ 65535 ];

				int length;

				while ( ( length = in.read(buffer) ) > 0 )
					out.write(buffer, 0, length);

				in.close();
				out.close();
			}
		}
	}

	/**
	 * Assembles a new SpimData2 based on the subset of timepoints and viewsetups as selected by the user.
	 * The imgloader is still not set here.
	 * 
	 * It also fills up a list of filesToCopy from the interestpoints directory if it is not null.
	 * 
	 * @param spimData - the source SpimData
	 * @param filesToCopy - list to be filled with files to copy
	 * @param viewIds - view subset to resave
	 * @param basePath - the base path
	 * @return new SpimData
	 */
	public static SpimData2 assemblePartialSpimData2( final SpimData2 spimData, final List< ? extends ViewId > viewIds, final File basePath, final List< String > filesToCopy )
	{
		final TimePoints timepoints;

		try
		{
			timepoints = new TimePointsPattern( listAllTimePoints( SpimData2.getAllTimePointsSorted( spimData, viewIds ) ) );
		}
		catch (ParseException e)
		{
			IOFunctions.println( "Automatically created list of timepoints failed to parse. This should not happen, really :) -- " + e );
			IOFunctions.println( "Here is the list: " + listAllTimePoints( SpimData2.getAllTimePointsSorted( spimData, viewIds ) ) );
			e.printStackTrace();
			return null;
		}

		final List< ViewSetup > setups = SpimData2.getAllViewSetupsSorted( spimData, viewIds );

		// a hashset for all viewsetups that remain
		final Set< ViewId > views = new HashSet< ViewId >();
		
		for ( final ViewId viewId : viewIds )
			views.add( new ViewId( viewId.getTimePointId(), viewId.getViewSetupId() ) );

		final MissingViews oldMissingViews = spimData.getSequenceDescription().getMissingViews();
		final HashSet< ViewId > missingViews = new HashSet< ViewId >();

		if ( oldMissingViews != null && oldMissingViews.getMissingViews() != null )
			for ( final ViewId id : oldMissingViews.getMissingViews() )
				if ( views.contains( id ) )
					missingViews.add( id );

		// add the new missing views!!!
		for ( final TimePoint t : timepoints.getTimePointsOrdered() )
			for ( final ViewSetup v : setups )
			{
				final ViewId viewId = new ViewId( t.getId(), v.getId() );

				if ( !views.contains( viewId ) )
					missingViews.add( viewId );
			}

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, setups, null, new MissingViews( missingViews ) );

		// re-assemble the registrations
		final Map< ViewId, ViewRegistration > oldRegMap = spimData.getViewRegistrations().getViewRegistrations();
		final Map< ViewId, ViewRegistration > newRegMap = new HashMap< ViewId, ViewRegistration >();
		
		for ( final ViewId viewId : oldRegMap.keySet() )
			if ( views.contains( viewId ) )
				newRegMap.put( viewId, oldRegMap.get( viewId ) );

		final ViewRegistrations viewRegistrations = new ViewRegistrations( newRegMap );
		
		// re-assemble the interestpoints and a list of filenames to copy
		final Map< ViewId, ViewInterestPointLists > oldInterestPoints = spimData.getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, ViewInterestPointLists > newInterestPoints = new HashMap< ViewId, ViewInterestPointLists >();

		for ( final ViewId viewId : oldInterestPoints.keySet() )
			if ( views.contains( viewId ) )
			{
				final ViewInterestPointLists ipLists = oldInterestPoints.get( viewId );
				newInterestPoints.put( viewId, ipLists );

				if ( filesToCopy != null )
				{
					// get also all the filenames that we need to copy
					for ( final InterestPointList ipl : ipLists.getHashMap().values() )
						filesToCopy.add( ipl.getFile().getName() );
				}
			}

		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( newInterestPoints );

		//TODO: copy PSFs

		final SpimData2 newSpimData = new SpimData2(
				basePath,
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints,
				spimData.getBoundingBoxes(),
				spimData.getPointSpreadFunctions(),
				spimData.getStitchingResults(),
				spimData.getIntensityAdjustments());

		return newSpimData;
	}
}
