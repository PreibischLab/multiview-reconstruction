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
package net.preibisch.mvrecon.process.quality;

import java.util.ArrayList;
import java.util.List;

import ij.process.FloatProcessor;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.Point;
import net.imglib2.PointSampleList;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

/**
 * Computes the fourier ring correlation at specific positions and interpolates between all points
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 * @param <T> pixel type
 */
public class FRCRealRandomAccessible< T extends RealType< T > > implements RealRandomAccessible< FloatType >
{
	/**
	 * The Img containing the approxmimated content-based weights
	 */
	final PointSampleList< FloatType > qualityList;
	final Interval interval;
	final int n;
	
	public FRCRealRandomAccessible(
			final RandomAccessibleInterval< T > input,
			final List< Point > locations,
			final int length )
	{
		this.n = input.numDimensions();

		this.interval = new FinalInterval( input );
		this.qualityList = new PointSampleList<>( input.numDimensions() );

		final RandomAccessible< FloatType > floatInput = Views.extendMirrorSingle( getFloatRAI( input ) );

		for ( final Point l : locations )
		{
			final double quality = computeFRC( floatInput, l, length );
			qualityList.add( l, new FloatType( (float)quality ) );
			
			System.out.println( l + ": " + quality );
		}
	}

	@Override
	public int numDimensions() { return qualityList.numDimensions(); }

	@Override
	public RealRandomAccess<FloatType> realRandomAccess()
	{ 
		return getRealRandomAccessible().realRandomAccess();
	}

	@Override
	public RealRandomAccess<FloatType> realRandomAccess( final RealInterval interval )
	{
		return realRandomAccess();
	}

	public RealRandomAccessible< FloatType > getRealRandomAccessible()
	{
		final NearestNeighborSearch< FloatType > search = new NearestNeighborSearchOnKDTree<>( new KDTree<>( qualityList ) );
		return Views.interpolate( search, new NearestNeighborSearchInterpolatorFactory< FloatType >() );
	}

	public RandomAccessibleInterval< FloatType > getRenderedQuality()
	{
		// InverseDistanceWeightingInterpolatorFactory
		final NearestNeighborSearch< FloatType > search = new NearestNeighborSearchOnKDTree<>( new KDTree<>( qualityList ) );
		final RealRandomAccessible< FloatType > realRandomAccessible = Views.interpolate( search, new NearestNeighborSearchInterpolatorFactory< FloatType >() );
		final RandomAccessible< FloatType > randomAccessible = Views.raster( realRandomAccessible );
		final RandomAccessibleInterval< FloatType > rai = Views.interval( randomAccessible, interval );

		return Views.interval( Views.extendZero( rai ), interval );
	}

	public PointSampleList< FloatType > getQualityList() { return qualityList; }

	public static double computeFRC(
			final RandomAccessible< FloatType > input,
			final Point location,
			final int length )

	{
		final Pair< FloatProcessor, FloatProcessor > fps = getTwoImagesA( input, location.getLongPosition( 0 ), location.getLongPosition( 1 ), location.getLongPosition( 2 ), length );

		final FRC frc = new FRC();

		// Get FIRE Number, assumes you have access to the two image processors.
		final double[][] frcCurve = frc.calculateFrcCurve( fps.getA(), fps.getB() );

		final double integral = integral( frcCurve );

		return integral;
	}

	public static double integral( final double[][] frcCurve )
	{
		double integral = 0;

		for ( int i = 0; i < frcCurve.length; ++i )
			integral += frcCurve[ i ][ 1 ];

		return integral;
	}

	public static Pair< FloatProcessor, FloatProcessor > getTwoImagesA( final RandomAccessible< FloatType > img, final long x, final long y, final long z, final int length )
	{
		final RandomAccessible< FloatType > s0 = Views.hyperSlice( img, 2, z );
		final RandomAccessible< FloatType > s1 = Views.hyperSlice( img, 2, z + 1 );

		final FloatProcessor fp0 = new FloatProcessor( length, length );
		final FloatProcessor fp1 = new FloatProcessor( length, length );

		final long minX = x - length/2;
		final long minY = y - length/2;

		final Cursor< FloatType > c0 = Views.iterable( Views.interval( s0, new long[]{ minX, minY }, new long[]{ x + length/2 - 1, y + length/2 - 1 } ) ).localizingCursor();
		final Cursor< FloatType > c1 = Views.iterable( Views.interval( s1, new long[]{ minX, minY }, new long[]{ x + length/2 - 1, y + length/2 - 1 } ) ).localizingCursor();

		while ( c0.hasNext() )
		{
			c0.fwd();
			c1.fwd();

			fp0.setf( c0.getIntPosition( 0 ) - (int)minX, c0.getIntPosition( 1 ) - (int)minY, c0.get().get() );
			fp1.setf( c1.getIntPosition( 0 ) - (int)minX, c1.getIntPosition( 1 ) - (int)minY, c1.get().get() );
		}

		return new ValuePair< FloatProcessor, FloatProcessor >( fp0, fp1 );
	}

	public static ArrayList< Pair< Long, Long > > fixedGridXY( final Interval interval, final long distance )
	{
		final long lx = interval.dimension( 0 );
		final long ly = interval.dimension( 1 );

		System.out.println( "lx: " + lx );
		System.out.println( "ly: " + ly );

		long sqX = Math.max( 1, lx / distance );
		if ( lx % distance > 0 ) ++sqX;

		long sqY = Math.max( 1, ly / distance );
		if ( ly % distance > 0 ) ++sqY;

		System.out.println( "SquaresX: " + sqX );
		System.out.println( "SquaresY: " + sqY );

		final ArrayList< Long > xPos = getLocations( sqX, lx );
		final ArrayList< Long > yPos = getLocations( sqY, ly );

		final ArrayList< Pair< Long, Long > > list = new ArrayList<>();

		for ( final long x : xPos )
			System.out.println( x );

		System.out.println();

		for ( final long y : yPos )
			System.out.println( y );

		for ( int y = 0; y < yPos.size(); ++y )
			for ( int x = 0; x < xPos.size(); ++x )
				list.add( new ValuePair< Long, Long >( xPos.get( x ) + interval.min( 0 ), yPos.get( y ) + interval.min( 1 ) ) );

		return list;
	}

	public static ArrayList< Pair< Long, Long > > distributeSquaresXY( final Interval interval, final long length, final double overlapTolerance )
	{
		final long lx = interval.dimension( 0 );
		final long ly = interval.dimension( 1 );

		System.out.println( "lx: " + lx );
		System.out.println( "ly: " + ly );

		long sqX = lx / length;
		final long modX = lx % length;

		long sqY = ly / length;
		final long modY = ly % length;

		System.out.println( "sqX: " + sqX + " modX: " + modX + " relX: " + (double)modX / (double)length );
		System.out.println( "sqY: " + sqY + " modY: " + modX + " relY: " + (double)modY / (double)length );

		if ( (double)modX / (double)length >= overlapTolerance || sqX == 0 )
			++sqX;

		if ( (double)modY / (double)length >= overlapTolerance || sqY == 0 )
			++sqY;

		System.out.println( "SquaresX: " + sqX );
		System.out.println( "SquaresY: " + sqY );

		final ArrayList< Long > xPos = getLocations( sqX, lx );
		final ArrayList< Long > yPos = getLocations( sqY, ly );

		for ( final long x : xPos )
			System.out.println( x );

		System.out.println();

		for ( final long y : yPos )
			System.out.println( y );

		final ArrayList< Pair< Long, Long > > list = new ArrayList<>();

		for ( int y = 0; y < yPos.size(); ++y )
			for ( int x = 0; x < xPos.size(); ++x )
				list.add( new ValuePair< Long, Long >( xPos.get( x ) + interval.min( 0 ), yPos.get( y ) + interval.min( 1 ) ) );

		return list;
	}

	protected static final ArrayList< Long > getLocations( final long numSq, final long imgSize )
	{
		final ArrayList< Long > pos = new ArrayList<>();

		final double dist = (double)imgSize / (double)numSq;
		final double inc = (double)imgSize / ( numSq * 2.0 );

		for ( int i = 0; i < numSq; ++i )
			pos.add( Math.round( inc + i * dist ) );

		return pos;
	}

	@SuppressWarnings("unchecked")
	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > getFloatRAI( final RandomAccessibleInterval< T > input )
	{
		if ( FloatType.class.isInstance( Views.iterable( input ).cursor().next() ) )
			return (RandomAccessibleInterval< FloatType >)input;
		else
			return new ConvertedRandomAccessibleInterval< T, FloatType >( input, new RealFloatConverter< T >(),  new FloatType() );
	}

	public static void main( String[] args )
	{
		Interval interval = new FinalInterval( new long[] { 0, 0 }, new long[] { 260, 128 } );

		fixedGridXY( interval, 50 );
		//distributeSquaresXY( interval, 256, 0.01 );
	}
}
