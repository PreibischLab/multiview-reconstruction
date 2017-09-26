package net.preibisch.mvrecon.fiji.spimdata.boundingbox;

import net.imglib2.util.Util;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RealPositionable;

public class BoundingBox implements Interval, Comparable< BoundingBox >
{
	protected int[] min, max;
	protected String title;

	public BoundingBox( final String title, final int[] min, final int[] max )
	{
		this.title = title;
		this.min = min;
		this.max = max;
	}

	public BoundingBox( final int[] min, final int[] max )
	{
		this.min = min;
		this.max = max;
		this.title = "DefaultBoundingBox";
	}

	public BoundingBox( final Interval interval )
	{
		this.min = new int[ interval.numDimensions() ];
		this.max = new int[ interval.numDimensions() ];

		for ( int d = 0; d < interval.numDimensions(); ++d )
		{
			min[ d ] = (int)interval.min( d );
			max[ d ] = (int)interval.max( d );
		}

		this.title = "DefaultBoundingBox";
	}

	public void setTitle( final String title ) { this.title = title; }
	public String getTitle() { return title; }

	public int[] getMin() { return min; }
	public int[] getMax() { return max; }

	/**
	 * @param downsampling - how much downsampling (TODO: remove)
	 * @return - the final dimensions including downsampling of this bounding box (to instantiate an img)
	 */
	public long[] getDimensions( final int downsampling )
	{
		final long[] dim = new long[ this.numDimensions() ];
		this.dimensions( dim );

		for ( int d = 0; d < this.numDimensions(); ++d )
			dim[ d ] /= downsampling;

		return dim;
	}

	@Override
	public long min( final int d ) { return min[ d ]; }

	@Override
	public void min( final long[] min )
	{
		for ( int d = 0; d < min.length; ++d )
			min[ d ] = this.min[ d ];
	}

	@Override
	public void min( final Positionable min ) { min.setPosition( this.min ); }

	@Override
	public long max( final int d ) { return max[ d ]; }

	@Override
	public void max( final long[] max )
	{
		for ( int d = 0; d < max.length; ++d )
			max[ d ] = this.max[ d ];
	}

	@Override
	public void max( final Positionable max ) { max.setPosition( this.max ); }

	@Override
	public double realMin( final int d ) { return min[ d ]; }

	@Override
	public void realMin( final double[] min )
	{
		for ( int d = 0; d < min.length; ++d )
			min[ d ] = this.min[ d ];
	}

	@Override
	public void realMin( final RealPositionable min ) { min.setPosition( this.min ); }

	@Override
	public double realMax( final int d ) { return this.max[ d ]; }

	@Override
	public void realMax( final double[] max )
	{
		for ( int d = 0; d < max.length; ++d )
			max[ d ] = this.max[ d ];
	}

	@Override
	public void realMax( final RealPositionable max ) { max.setPosition( this.max ); }

	@Override
	public int numDimensions() { return min.length; }

	@Override
	public void dimensions( final long[] dimensions )
	{
		for ( int d = 0; d < max.length; ++d )
			dimensions[ d ] = dimension( d );
	}

	@Override
	public long dimension( final int d ) { return this.max[ d ] - this.min[ d ] + 1; }

	@Override
	public int compareTo( final BoundingBox o ) { return o.getTitle().compareTo( this.getTitle() ); }

	@Override
	public String toString()
	{
		return "Bounding Box '" + getTitle() + "' " + Util.printCoordinates( min ) + " >>> " + Util.printCoordinates( max );
	}

	public static String getBoundingBoxDescription( final BoundingBox bb )
	{
		String title = bb.getTitle() + " (dim=";

		for ( int d = 0; d < bb.numDimensions(); ++d )
			title += bb.dimension( d ) + "x";

		title = title.substring( 0, title.length() - 1 ) + "px, offset=";

		for ( int d = 0; d < bb.numDimensions(); ++d )
			title += bb.min( d ) + "x";

		title = title.substring( 0, title.length() - 1 ) + "px)";

		return title;
	}
}
