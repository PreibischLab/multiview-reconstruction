package net.preibisch.mvrecon.process.fusion.intensity.mpicbg;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import mpicbg.models.PointMatch;

public class FlattenedMatches
{
	private final int n;
	private final int size;
	private final double[][] p;
	private final double[][] q;
	private final double[] w;

	public FlattenedMatches( final int numDimensions, final int size )
	{
		this.n = numDimensions;
		this.size = size;
		p = new double[ numDimensions ][ size ];
		q = new double[ numDimensions ][ size ];
		w = new double[ size ];
	}

	private FlattenedMatches( final FlattenedMatches other, final int size )
	{
		n = other.n;
		this.size = size;
		p = new double[ n ][];
		Arrays.setAll( p, d -> Arrays.copyOf( other.p[ d ], size ) );
		q = new double[ n ][];
		Arrays.setAll( q, d -> Arrays.copyOf( other.q[ d ], size ) );
		w = Arrays.copyOf( other.w, size );
	}

	public static FlattenedMatches copyOf( final FlattenedMatches matches, final int newSize )
	{
		return new FlattenedMatches( matches, newSize );
	}

	public < P extends PointMatch > FlattenedMatches( final Collection< P > matches )
	{
		this( numDimensions( matches ), matches.size() );

		final Iterator< P > iter = matches.iterator();
		for ( int i = 0; i < size; i++ )
		{
			final P match = iter.next();
			final double[] l1 = match.getP1().getL();
			final double[] l2 = match.getP2().getL();
			for ( int d = 0; d < n; d++ )
			{
				p[ d ][ i ] = l1[ d ];
				q[ d ][ i ] = l2[ d ];
			}
			w[ i ] = match.getWeight();
		}
	}

	public int size()
	{
		return size;
	}

	public int numDimensions()
	{
		return n;
	}

	public double[][] p()
	{
		return p;
	}

	public double[][] q()
	{
		return q;
	}

	public double[] w()
	{
		return w;
	}

	private static < P extends PointMatch > int numDimensions( final Collection< P > matches )
	{
		if ( matches.isEmpty() )
			throw new IllegalArgumentException( "There must be at least one match to determine number of dimensions." );

		return matches.iterator().next().getP1().getL().length;
	}
}
