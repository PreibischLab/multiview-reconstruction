package net.preibisch.mvrecon.process.resave;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;

public class SpimData2Tools
{
	public static boolean loadDimensions( final SpimData2 spimData, final List< ViewSetup > viewsetups )
	{
		boolean loadedDimensions = false;

		for ( final ViewSetup vs : viewsetups )
		{
			if ( vs.getSize() == null )
			{
				IOFunctions.println( "Dimensions of viewsetup " + vs.getId() + " unknown. Loading them ... " );

				for ( final TimePoint t : spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
				{
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( t.getId(), vs.getId() );

					if ( vd.isPresent() )
					{
						Dimensions dim = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImageSize( vd.getTimePointId() );

						IOFunctions.println(
								"Dimensions: " + dim.dimension( 0 ) + "x" + dim.dimension( 1 ) + "x" + dim.dimension( 2 ) +
								", loaded from tp:" + t.getId() + " vs: " + vs.getId() );

						vs.setSize( dim );
						loadedDimensions = true;
						break;
					}
					else
					{
						IOFunctions.println( "ViewSetup: " + vs.getId() + " not present in timepoint: " + t.getId() );
					}
				}
			}
		}

		return loadedDimensions;
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
	public static SpimData2 assemblePartialSpimData2( final SpimData2 spimData, final List< ViewId > viewIds, final File basePath, final List< String > filesToCopy )
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

	/**
	 * Reduces a given SpimData2 to the subset of timepoints and viewsetups as selected by the user, including the original imgloader.
	 *
	 * @param oldSpimData - the original SpimData
	 * @param viewIds - the views to keep
	 * @return - reduced SpimData2
	 */
	public static SpimData2 reduceSpimData2( final SpimData2 oldSpimData, final List< ViewId > viewIds )
	{
		final TimePoints timepoints;

		try
		{
			timepoints = new TimePointsPattern( listAllTimePoints( SpimData2.getAllTimePointsSorted( oldSpimData, viewIds ) ) );
		}
		catch (ParseException e)
		{
			IOFunctions.println( "Automatically created list of timepoints failed to parse. This should not happen, really :) -- " + e );
			IOFunctions.println( "Here is the list: " + listAllTimePoints( SpimData2.getAllTimePointsSorted( oldSpimData, viewIds ) ) );
			e.printStackTrace();
			return null;
		}

		final List< ViewSetup > viewSetupsToProcess = SpimData2.getAllViewSetupsSorted( oldSpimData, viewIds );

		// a hashset for all viewsetups that remain
		final Set< ViewId > views = new HashSet< ViewId >();

		for ( final ViewId viewId : viewIds )
			views.add( new ViewId( viewId.getTimePointId(), viewId.getViewSetupId() ) );

		final MissingViews oldMissingViews = oldSpimData.getSequenceDescription().getMissingViews();
		final HashSet< ViewId > missingViews = new HashSet< ViewId >();

		if( oldMissingViews != null && oldMissingViews.getMissingViews() != null )
			for ( final ViewId id : oldMissingViews.getMissingViews() )
				if ( views.contains( id ) )
					missingViews.add( id );

		// add the new missing views!!!
		for ( final TimePoint t : timepoints.getTimePointsOrdered() )
			for ( final ViewSetup v : viewSetupsToProcess )
			{
				final ViewId viewId = new ViewId( t.getId(), v.getId() );

				if ( !views.contains( viewId ) )
					missingViews.add( viewId );
			}

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, viewSetupsToProcess, oldSpimData.getSequenceDescription().getImgLoader(), new MissingViews( missingViews ) );

		// re-assemble the registrations
		final Map< ViewId, ViewRegistration > oldRegMap = oldSpimData.getViewRegistrations().getViewRegistrations();
		final Map< ViewId, ViewRegistration > newRegMap = new HashMap< ViewId, ViewRegistration >();

		for ( final ViewId viewId : oldRegMap.keySet() )
			if ( views.contains( viewId ) )
				newRegMap.put( viewId, oldRegMap.get( viewId ) );

		final ViewRegistrations viewRegistrations = new ViewRegistrations( newRegMap );

		// re-assemble the interestpoints and a list of filenames to copy
		final Map< ViewId, ViewInterestPointLists > oldInterestPoints = oldSpimData.getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, ViewInterestPointLists > newInterestPoints = new HashMap< ViewId, ViewInterestPointLists >();

		for ( final ViewId viewId : oldInterestPoints.keySet() )
			if ( views.contains( viewId ) )
				newInterestPoints.put( viewId, oldInterestPoints.get( viewId ) );

		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( newInterestPoints );

		//TODO: copy PSFs

		final SpimData2 newSpimData = new SpimData2(
				oldSpimData.getBasePath(),
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints,
				oldSpimData.getBoundingBoxes(),
				oldSpimData.getPointSpreadFunctions(),
				oldSpimData.getStitchingResults(),
				oldSpimData.getIntensityAdjustments() );

		return newSpimData;
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
}
