package net.preibisch.mvrecon.process.fusion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class FusionUtils {
	public static int defaultCache = 2;
	public static int[] cellDim = new int[]{ 10, 10, 10 };
	public static int maxCacheSize = 1000000;

	public static double defaultDownsampling = 1.0;
	public static int defaultBB = 0;
	

	
	public static boolean isImgLoaderVirtual( final SpimData spimData )
	{
		if ( MultiResolutionImgLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ) )
			return true;

		// TODO: check for Davids virtual implementation of the normal imgloader
		return false;
	}
	
	public static boolean isMultiResolution( final SpimData spimData )
	{
		if ( MultiResolutionImgLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ) )
			return true;
		else
			return false;
	}
	
	public static String[] getBoundingBoxChoices( final List< BoundingBox > allBoxes)
	{
		return getBoundingBoxChoices( allBoxes, true );
	}
	
	public static String[] getBoundingBoxChoices( final List< BoundingBox > allBoxes, boolean showDimensions )
	{
		final String[] choices = new String[ allBoxes.size() ];

		int i = 0;
		for ( final BoundingBox b : allBoxes )
			choices[ i++ ] = showDimensions 
								? b.getTitle() + " (" + b.dimension( 0 ) + "x" + b.dimension( 1 ) + "x" + b.dimension( 2 ) + "px)"
								: b.getTitle();

		return choices;
	}
	
	public static List< Group< ViewDescription > > getFusionGroups( final SpimData2 spimData, final List< ViewId > views, final int splittingType )
	{
		final ArrayList< ViewDescription > vds = SpimData2.getAllViewDescriptionsSorted( spimData, views );
		final List< Group< ViewDescription > > grouped;

		if ( splittingType < 2 ) // "Each timepoint & channel" or "Each timepoint, channel & illumination"
		{
			final HashSet< Class< ? extends Entity > > groupingFactors = new HashSet<>();

			groupingFactors.add( TimePoint.class );
			groupingFactors.add( Channel.class );

			if ( splittingType == 1 ) // "Each timepoint, channel & illumination"
				groupingFactors.add( Illumination.class );

			grouped = Group.splitBy( vds, groupingFactors );
		}
		else if ( splittingType == 2 ) // "All views together"
		{
			final Group< ViewDescription > allViews = new Group<>( vds );
			grouped = new ArrayList<>();
			grouped.add( allViews );
		}
		else // "All views"
		{
			grouped = new ArrayList<>();
			for ( final ViewDescription vd : vds )
				grouped.add( new Group<>( vd ) );
		}

		return grouped;
	}

	public static long maxNumInputPixelsPerInputGroup( final SpimData2 spimData, final List< ViewId > views, final int splittingType )
	{
		long maxNumPixels = 0;

		for ( final Group< ViewDescription > group : getFusionGroups( spimData, views, splittingType ) )
		{
			long numpixels = 0;

			for ( final ViewDescription vd : group )
				numpixels += Intervals.numElements( vd.getViewSetup().getSize() );

			maxNumPixels = Math.max( maxNumPixels, numpixels );
		}

		return maxNumPixels;
	}
	
	public static int inputBytePerPixel( final ViewId viewId, final SpimData2 spimData )
	{
		SetupImgLoader< ? > loader = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( viewId.getViewSetupId() );
		Object type = loader.getImageType();

		if ( UnsignedByteType.class.isInstance( type ) )
			return 1;
		else if ( UnsignedShortType.class.isInstance( type ) )
			return 2;
		else
			return 4;
	}

}
