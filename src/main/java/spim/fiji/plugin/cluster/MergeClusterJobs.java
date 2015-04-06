package spim.fiji.plugin.cluster;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class MergeClusterJobs
{
	/**
	 * This performs a merge of attributes of the different xml's only assuming the same instances of viewsetups and timepoints
	 * are present in all instances. It simply fills up the first one that can be written again. 
	 * 
	 * @param xmls
	 * @param output - where to save the merged xml
	 * @throws SpimDataException
	 */
	public static void merge( final List< File > xmls, final File output ) throws SpimDataException
	{
		final ArrayList< Pair< XmlIoSpimData2, SpimData2 > > instances = new ArrayList< Pair< XmlIoSpimData2, SpimData2 > >();

		for ( final File xml : xmls )
		{
			final XmlIoSpimData2 io = new XmlIoSpimData2( "" );
			final SpimData2 data = io.load( xml.getAbsolutePath() );

			instances.add( new ValuePair< XmlIoSpimData2, SpimData2 >( io, data ) );
		}

		final XmlIoSpimData2 ioOut = instances.get( 0 ).getA();
		final SpimData2 dataOut = instances.get( 0 ).getB();
		final ViewRegistrations vrOut = dataOut.getViewRegistrations();
		final ViewInterestPoints vipOut = dataOut.getViewInterestPoints();

		//
		// merge XML's
		//
		for ( int i = 1; i < instances.size(); ++i )
		{
			final SpimData2 data = instances.get( i ).getB();

			// 
			// Update attributes of viewsetups 
			//
			for ( final ViewSetup v : data.getSequenceDescription().getViewSetupsOrdered() )
			{
				final ViewSetup vOut = dataOut.getSequenceDescription().getViewSetups().get( v.getId() );

				if ( vOut == null )
					throw new SpimDataException(
							"Could not find corresponding ViewSetupId=" + v.getId() + " in xml '" + xmls.get( i ) + "'" );

				if ( vOut.getSize() == null && v.getSize() != null )
					vOut.setSize( v.getSize() );

				if ( vOut.getVoxelSize() == null && v.getVoxelSize() != null )
					vOut.setVoxelSize( v.getVoxelSize() );

				if ( !vOut.getAngle().hasRotation() && v.getAngle().hasRotation() )
					vOut.getAngle().setRotation( v.getAngle().getRotationAxis(), v.getAngle().getRotationAngleDegrees() );
			}

			//
			// update viewregistrations, choose longest one
			//
			for ( final ViewRegistration v : data.getViewRegistrations().getViewRegistrationsOrdered() )
			{
				// get the corresponding viewRegistration
				final ViewRegistration vOut = vrOut.getViewRegistration( v );

				if ( vOut == null )
					throw new SpimDataException(
							"Could not find corresponding ViewRegistration for Timepoint=" + v.getTimePointId() +
							" ViewSetupId=" + v.getViewSetupId() + " in xml '" + xmls.get( i ) + "'" );

				// take the longer viewtransformlist
				if ( v.getTransformList().size() > vOut.getTransformList().size() )
				{
					vOut.getTransformList().clear();
					vOut.getTransformList().addAll( v.getTransformList() );
					vOut.updateModel();
				}
			}

			//
			// update viewinterestpoints, add those who do not exist
			//
			final ViewInterestPoints vip = data.getViewInterestPoints();

			for ( final TimePoint tp : data.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
				for ( final ViewSetup vs : data.getSequenceDescription().getViewSetupsOrdered() )
				{
					final ViewDescription vd = data.getSequenceDescription().getViewDescription( tp.getId(), vs.getId() );

					if ( vd.isPresent() )
					{
						final ViewInterestPointLists vipl = vip.getViewInterestPointLists( tp.getId(), vs.getId() );
						final ViewInterestPointLists viplOut = vipOut.getViewInterestPointLists( tp.getId(), vs.getId() );

						// add the objects
						final HashMap< String, InterestPointList > map = vipl.getHashMap();
						for ( final String label : map.keySet() )
						{
							final InterestPointList ipl = map.get( label );
							viplOut.addInterestPointList( label, ipl );
						}
					}
				}
		}

		// save the XML
		try
		{
			ioOut.save( dataOut, output.getAbsolutePath() );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + output + "'." );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + output + "': " + e );
			e.printStackTrace();
		}
	}
}