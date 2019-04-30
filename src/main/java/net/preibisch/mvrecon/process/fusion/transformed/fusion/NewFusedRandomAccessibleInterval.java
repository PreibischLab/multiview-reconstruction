/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.fusion.transformed.fusion;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.list.ListImg;
import net.imglib2.iterator.ZeroMinIntervalIterator;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class NewFusedRandomAccessibleInterval implements RandomAccessibleInterval< FloatType >
{
	final int n;
	final long[] min; // in world coordinates

	final Interval interval;
	final List< ? extends RandomAccessible< FloatType > > images;
	final List< ? extends RandomAccessible< FloatType > > weights;

	final List< Interval > boundingBoxes;
	final ListImg< int[] > lookUpGrid;
	final long distance[];

	// distance between lookup points
	// can be smaller if minDim is smaller than that value
	final long maxDist;

	public < R extends RandomAccessible< FloatType > & BoundingBoxable > NewFusedRandomAccessibleInterval(
			final Interval interval,
			final List< ? extends R > images,
			final List< ? extends RandomAccessible< FloatType > > weights,
			final long maxDist )
	{
		this.n = interval.numDimensions();
		this.interval = interval;
		this.images = images;
		this.maxDist = maxDist;
		this.boundingBoxes = new ArrayList<>();

		for ( final R img : images )
			boundingBoxes.add( new FinalInterval( img.getBoundingBox() ) );

		this.weights = weights;
		if ( this.images.size() != this.weights.size() )
			throw new RuntimeException( "Images and weights do not have the same size: " + images.size() + " != " + weights.size() );

		this.min = new long[ n ]; // in world coordinates

		for ( int d = 0; d < n; ++d )
			min[ d ] = interval.min( d );

		final Pair< ListImg< int[] >, long[] > pair = setupLookup();
		this.lookUpGrid = pair.getA();
		this.distance = pair.getB();
	}

	public < R extends RandomAccessible< FloatType > & BoundingBoxable > NewFusedRandomAccessibleInterval(
			final Interval interval,
			final List< ? extends R > images,
			final List< ? extends RandomAccessible< FloatType > > weights )
	{
		this( interval, images, weights, 50 );
	}

	public Interval getInterval() { return interval; }
	public List< ? extends RandomAccessible< FloatType > > getImages() { return images; }
	public List< ? extends RandomAccessible< FloatType > > getWeights() { return weights; }
	public List< Interval > getBoundingBoxes() { return boundingBoxes; }

	public RandomAccessibleInterval< IntType > renderLookup()
	{
		final RandomAccessibleInterval< IntType > rendered = Views.translate( new CellImgFactory< IntType >( new IntType() ).create( interval ), min );

		final Cursor< IntType > c = Views.iterable( rendered ).localizingCursor();
		final RandomAccess< int[] > r = lookUpGrid.randomAccess();

		final long[] pos = new long[ n ];

		while ( c.hasNext() )
		{
			c.fwd();

			getLocalCoordinates( pos, c, min, distance, n );

			r.setPosition( pos );

			c.get().set( r.get().length );
		}

		return rendered;
	}

	public Pair< ListImg< int[] >, long[] > setupLookup()
	{
		// minDim defines the maximum spacing for the lookup to never miss a bounding box
		final long[] minDim = new long[ n ];
		for ( int d = 0; d < n; ++d )
			minDim[ d ] = Long.MAX_VALUE;

		for ( final Interval boundingBox : boundingBoxes )
			for ( int d = 0; d < n; ++d )
				minDim[ d ] = Math.min( minDim[ d ], boundingBox.dimension( d ) );

		final long distance[] = new long[ n ];

		for ( int d = 0; d < n; ++d )
			distance[ d ] = Math.min( maxDist, minDim[ d ] );

		final long[] dim = new long[ n ]; // in actual scaled pixels

		for ( int d = 0; d < n; ++d )
		{
			dim[ d ] = interval.dimension( d ) / distance[ d ] + 1;

			if ( interval.dimension( d ) % distance[ d ] != 0 )
				++dim[ d ];
		}

		final ArrayList< int[] > overlapIds = new ArrayList<>();

		final ZeroMinIntervalIterator it = new ZeroMinIntervalIterator( dim );
		final long[] pos = new long[ n ];

		while ( it.hasNext() )
		{
			it.fwd();

			getWorldCoordinates( pos, it, min, distance, n );

			final ArrayList< Integer > idList = new ArrayList<>();

			for ( int id = 0; id < boundingBoxes.size(); ++id )
			{
				final Interval interval = boundingBoxes.get( id );

				if ( isOverlapping( pos, interval, distance ) )
					idList.add( id );
			}

			overlapIds.add( idList.stream().mapToInt( i -> i ).toArray() );
		}

		return new ValuePair<>( new ListImg<>( overlapIds, dim ), distance ); 
	}

	protected boolean isOverlapping( final long[] pos, final Interval interval, final long distance[] )
	{
		if ( contains( pos, interval, n ) )
			return true;

		for ( int d = 0; d < n; ++d )
		{
			pos[ d ] += distance[ d ] / 2;

			if ( contains( pos, interval, n ) )
				return true;

			pos[ d ] -= distance[ d ] / 2; //even-odd
			pos[ d ] -= distance[ d ] / 2;

			if ( contains( pos, interval, n ) )
				return true;

			pos[ d ] += distance[ d ] / 2;
		}

		return false;
	}

	final static protected boolean contains( final long[] pos, final Interval interval, final int n )
	{
		for ( int d = 0; d < n; ++d )
		{
			if ( pos[ d ] < interval.min( d ) || pos[ d ] > interval.max( d ) )
				return false;
		}

		return true;
	}

	protected static final void getWorldCoordinates( final long[] pos, final Localizable l, final long[] min, final long[] distance, final int n )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = l.getLongPosition( d ) * distance[ d ] + min[ d ];
	}

	protected static final void getLocalCoordinates( final long[] pos, final Localizable l, final long[] min, final long[] distance, final int n )
	{
		for ( int d = 0; d < n; ++d )
			pos[ d ] = Math.round( ( l.getLongPosition( d ) - min[ d ] ) / distance[ d ] );
	}

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		return new NewFusedRandomAccess( n, images, weights );
	}

	@Override
	public RandomAccess< FloatType > randomAccess( Interval interval )
	{
		return randomAccess();
	}

	@Override
	public long min( final int d ) { return interval.min( d ); }

	@Override
	public void min( final long[] min ) { interval.min( min ); }

	@Override
	public void min( final Positionable min ) { interval.min( min ); }

	@Override
	public long max( final int d ) { return interval.max( d ); }

	@Override
	public void max( final long[] max ) { interval.max( max ); }

	@Override
	public void max( final Positionable max )  { interval.max( max ); }

	@Override
	public double realMin( final int d ) { return interval.realMin( d ); }

	@Override
	public void realMin( final double[] min ) { interval.realMin( min ); }

	@Override
	public void realMin( final RealPositionable min ) { interval.realMin( min ); }

	@Override
	public double realMax( final int d ) { return interval.realMax( d ); }

	@Override
	public void realMax( final double[] max ) { interval.realMax( max ); }

	@Override
	public void realMax( final RealPositionable max ) { interval.realMax( max ); }

	@Override
	public void dimensions( final long[] dimensions ) { interval.dimensions( dimensions ); }

	@Override
	public long dimension( final int d ) { return interval.dimension( d ); }
}
