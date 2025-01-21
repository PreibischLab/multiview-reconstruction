/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap;

import java.util.Map;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;

public class SimpleBoundingBoxOverlap< V extends ViewId > implements OverlapDetection< V >
{
	final Map< ViewId, ViewRegistration > vrs;
	final Map< Integer, ? extends BasicViewSetup > vss;

	public SimpleBoundingBoxOverlap( final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ?, ? > > spimData )
	{
		this.vss = spimData.getSequenceDescription().getViewSetups();
		this.vrs = spimData.getViewRegistrations().getViewRegistrations();
	}

	public SimpleBoundingBoxOverlap( final SequenceDescription sd, final ViewRegistrations vrs )
	{
		this.vss = sd.getViewSetups();
		this.vrs = vrs.getViewRegistrations();
	}

	public SimpleBoundingBoxOverlap( final Map< Integer, ? extends BasicViewSetup > viewSetups, final Map< ViewId, ViewRegistration >  vrs )
	{
		this.vss = viewSetups;
		this.vrs = vrs;
	}

	@Override
	public boolean overlaps( final V view1, final V view2 )
	{
		final BoundingBox bb1 = getBoundingBox( vss.get( view1.getViewSetupId() ), vrs.get( view1 ) );
		final BoundingBox bb2 = getBoundingBox( vss.get( view2.getViewSetupId() ), vrs.get( view2 ) );

		if ( bb1 == null )
			throw new RuntimeException( "view1 has no image size" );

		if ( bb2 == null )
			throw new RuntimeException( "view2 has no image size" );

		return overlaps( bb1, bb2 );
	}

	@Override
	public RealInterval getOverlapInterval( final V view1, final V view2 )
	{
		final RealInterval bb1 = getBoundingBoxReal( vss.get( view1.getViewSetupId() ), vrs.get( view1 ) );
		final RealInterval bb2 = getBoundingBoxReal( vss.get( view2.getViewSetupId() ), vrs.get( view2 ) );

		if ( bb1 == null )
			throw new RuntimeException( "view1 has no image size" );

		if ( bb2 == null )
			throw new RuntimeException( "view2 has no image size" );

		double[] min = new double[ bb1.numDimensions() ];
		double[] max = new double[ bb1.numDimensions() ];

		if ( overlaps( getBoundingBox( vss.get( view1.getViewSetupId() ), vrs.get( view1 ) ), getBoundingBox( vss.get( view2.getViewSetupId() ), vrs.get( view2 ) ) ) )
		{
			for ( int d = 0; d < bb1.numDimensions(); ++d )
			{
				min[ d ] = Math.max( bb1.realMin( d ), bb2.realMin( d ) );
				max[ d ] = Math.min( bb1.realMax( d ), bb2.realMax( d ) );

				// is 2d?
				if ( min[ d ] == max[ d ] && d == 2 && min[ d ] == 0 && vss.get( view1.getViewSetupId() ).getSize().dimension( 2 ) == 1 && vss.get( view2.getViewSetupId() ).getSize().dimension( 2 ) == 1)
				{
					min[ d ] = 0.0;
					max[ d ] = 1.0;
				}
				else if ( min[ d ] == max[ d ] || max[ d ] < min[ d ] )
				{
					return null;
				}
			}

			return new FinalRealInterval( min, max );
		}
		else
		{
			return null;
		}
	}

	public static boolean overlaps( final BoundingBox bb1, final BoundingBox bb2 )
	{
		for ( int d = 0; d < bb1.numDimensions(); ++d )
		{
			if (
				bb1.getMin()[ d ] < bb2.getMin()[ d ] && bb1.getMax()[ d ] < bb2.getMin()[ d ] ||
				bb1.getMin()[ d ] > bb2.getMax()[ d ] && bb1.getMax()[ d ] > bb2.getMax()[ d ] )
			{
				return false;
			}
		}

		return true;
	}

	public static < V extends ViewId > BoundingBox getBoundingBox(
			final V view,
			final Map< Integer, ? extends BasicViewSetup > vss,
			final ViewRegistrations vrs )
	{
		return getBoundingBox( vss.get( view.getViewSetupId() ), vrs.getViewRegistration( view ) );
	}

	public static < V extends ViewId > RealInterval getBoundingBoxReal(
			final V view,
			final Map< Integer, ? extends BasicViewSetup > vss,
			final ViewRegistrations vrs )
	{
		return getBoundingBoxReal( vss.get( view.getViewSetupId() ), vrs.getViewRegistration( view ) );
	}

	public static BoundingBox getBoundingBox(
			final BasicViewSetup vs,
			final ViewRegistration vr )
	{
		if ( !vs.hasSize() )
			return null;

		vr.updateModel();

		return getBoundingBox( vs.getSize(), vr.getModel() );
	}

	public static BoundingBox getBoundingBox( final Dimensions dims, final AffineTransform3D transform )
	{
		final RealInterval interval = getBoundingBoxReal( dims, transform );

		final int[] minInt = new int[ 3 ];
		final int[] maxInt = new int[ 3 ];

		for ( int d = 0; d < dims.numDimensions(); ++d )
		{
			minInt[ d ] = (int)Math.round( interval.realMin( d ) ) - 1;
			maxInt[ d ] = (int)Math.round( interval.realMax( d ) ) + 1;
		}

		return new BoundingBox( minInt, maxInt );
	}

	public static RealInterval getBoundingBoxReal(
			final BasicViewSetup vs,
			final ViewRegistration vr )
	{
		if ( !vs.hasSize() )
			return null;

		vr.updateModel();

		return getBoundingBoxReal( vs.getSize(), vr.getModel() );
	}

	public static RealInterval getBoundingBoxReal( final Dimensions dims, final AffineTransform3D transform )
	{
		final double[] min = new double[]{ 0, 0, 0 };
		final double[] max = new double[]{
				dims.dimension( 0 ) - 1,
				dims.dimension( 1 ) - 1,
				dims.dimension( 2 ) - 1 };

		return transform.estimateBounds( new FinalRealInterval( min, max ) );
	}
}
