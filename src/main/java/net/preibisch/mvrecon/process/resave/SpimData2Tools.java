package net.preibisch.mvrecon.process.resave;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.ParseException;
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
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;

public class SpimData2Tools
{

	/**
	 * Reduces a given SpimData2 to the subset of timepoints and viewsetups as selected by the user,
	 * including the original imgloader and keeping the basepath (i.e. interest points still work)
	 * 
	 * Note: PSF's will be lost.
	 *
	 * @param oldSpimData - the original SpimData
	 * @param viewIds - the views to keep
	 * @return - reduced SpimData2
	 */
	public static SpimData2 reduceSpimData2( final SpimData2 oldSpimData, final List< ViewId > viewIds )
	{
		return reduceSpimData2( oldSpimData, viewIds, null );
	}

	/**
	 * Reduces a given SpimData2 to the subset of timepoints and viewsetups as selected by the user,
	 * including the original imgloader and changing the base path (you still need to save to materialize the returned object!)
	 *
	 * Note: PSF's will be lost.
	 *
	 * @param oldSpimData - the original SpimData
	 * @param viewIds - the views to keep
	 * @param basePath - the new base path (can be null); if you set a new base path it will load all interest points so the new SpimData2 object can be saved including these points
	 * @return - reduced SpimData2
	 */
	public static SpimData2 reduceSpimData2( final SpimData2 oldSpimData, final List< ViewId > viewIds, final URI basePath )
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

		oldInterestPoints.forEach( (viewId, ipLists) ->
		{
			if ( views.contains( viewId ) )
			{
				if ( basePath != null )
				{
					final ViewInterestPointLists ipListsNew = new ViewInterestPointLists(viewId.getTimePointId(), viewId.getViewSetupId() );

					ipLists.getHashMap().forEach( (label,interestpoints) ->
					{
						final List<InterestPoint> points = interestpoints.getInterestPointsCopy();
						final List<CorrespondingInterestPoints> corr = interestpoints.getCorrespondingInterestPointsCopy();

						final InterestPoints interestpointsNew = InterestPoints.newInstance( basePath, viewId, label );
						interestpointsNew.setInterestPoints( points );
						interestpointsNew.setCorrespondingInterestPoints( corr );

						ipListsNew.addInterestPointList( label, interestpointsNew );
					} );

					newInterestPoints.put( viewId, ipListsNew );
				}
				else
				{
					// if the basepath doesn't change we can keep interestpoints as-is
					newInterestPoints.put( viewId, ipLists );
				}
			}
		});

		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( newInterestPoints );

		//TODO: copy PSFs?

		final SpimData2 newSpimData = new SpimData2(
				basePath == null ? oldSpimData.getBasePathURI() : basePath,
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints,
				oldSpimData.getBoundingBoxes(),
				new PointSpreadFunctions(), //oldSpimData.getPointSpreadFunctions()
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

	private static void copyFolder( final File src, final File dest, final List< String > filesToCopy ) throws IOException
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
