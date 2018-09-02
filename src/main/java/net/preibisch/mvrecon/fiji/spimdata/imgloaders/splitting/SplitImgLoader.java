package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import java.util.ArrayList;
import java.util.HashMap;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.iterator.LocalizingZeroMinIntervalIterator;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.process.cuda.Block;

public class SplitImgLoader implements ImgLoader
{
	final ImgLoader underlyingImgLoader;

	/**
	 * Maps the newly assigned ViewSetupId to the old ViewSetupId
	 */
	final HashMap< Integer, Integer > new2oldSetupId;

	/**
	 * Maps the newly assigned ViewSetupId to Interval inside the old ViewSetupId
	 */
	final HashMap< Integer, Interval > newSetupId2Interval;

	/**
	 * Remembers instances of SplitSetupImgLoader
	 */
	private final HashMap< Integer, SplitSetupImgLoader< ? > > splitSetupImgLoaders;

	public SplitImgLoader(
			final ImgLoader underlyingImgLoader,
			final HashMap< Integer, Integer > new2oldSetupId,
			final HashMap< Integer, Interval > newSetupId2Interval )
	{
		this.underlyingImgLoader = underlyingImgLoader;
		this.new2oldSetupId = new2oldSetupId;
		this.newSetupId2Interval = newSetupId2Interval;
		this.splitSetupImgLoaders = new HashMap<>();
	}

	@Override
	public SplitSetupImgLoader< ? > getSetupImgLoader( final int setupId )
	{
		return getSplitSetupImgLoader( underlyingImgLoader, new2oldSetupId.get( setupId ), setupId, newSetupId2Interval.get( setupId ) );
	}

	private final synchronized < T > SplitSetupImgLoader< ? > getSplitSetupImgLoader( final ImgLoader underlyingImgLoader, final int oldSetupId, final int newSetupId, final Interval interval )
	{
		SplitSetupImgLoader< ? > sil = splitSetupImgLoaders.get( newSetupId );
		if ( sil == null )
		{
			sil = createNewSetupImgLoader( underlyingImgLoader.getSetupImgLoader( oldSetupId ), interval );
			splitSetupImgLoaders.put( newSetupId, sil );
		}
		return sil;
	}

	private final synchronized < T > SplitSetupImgLoader< ? > createNewSetupImgLoader( final SetupImgLoader< T > setupImgLoader, final Interval interval )
	{
		return new SplitSetupImgLoader< T >( setupImgLoader, interval );
	}

	public static ArrayList< Interval > distributeIntervalsFixedOverlap( final Interval input, final long[] overlapPx, final long[] targetSize )
	{
		final ArrayList< ArrayList< Pair< Long, Long > > > intervalBasis = new ArrayList<>();

		for ( int d = 0; d < input.numDimensions(); ++d )
		{
			final ArrayList< Pair< Long, Long > > dimIntervals = new ArrayList<>();
	
			final long length = input.dimension( d );

			// can I use just 1 block?
			if ( length <= targetSize[ d ] )
			{
				final long min = input.min( d );
				final long max = input.max( d );

				dimIntervals.add( new ValuePair< Long, Long >( min, max ) );
				System.out.println( "one block from " + min + " to " + max );
			}
			else
			{
				final double l = length;
				final double s = targetSize[ d ];
				final double o = overlapPx[ d ];
	
				final double numCenterBlocks = ( l - 2.0 * ( s-o ) - o ) / ( s - 2.0 * o + o );
				final long numCenterBlocksInt;

				if ( numCenterBlocks <= 0.0 )
					numCenterBlocksInt = 0;
				else
					numCenterBlocksInt = Math.round( numCenterBlocks );

				final double n = numCenterBlocksInt;

				final double newSize = ( l + o + n * o ) / ( 2.0 + n );
				final long newSizeInt = Math.round( newSize );

				System.out.println( "numCenterBlocks: " + numCenterBlocks );
				System.out.println( "numCenterBlocksInt: " + numCenterBlocksInt );
				System.out.println( "numBlocks: " + (numCenterBlocksInt + 2) );
				System.out.println( "newSize: " + newSize );
				System.out.println( "newSizeInt: " + newSizeInt );

				System.out.println();
				//System.out.println( "block 0: " + input.min( d ) + " " + (input.min( d ) + Math.round( newSize ) - 1) );

				for ( int i = 0; i <= numCenterBlocksInt; ++i )
				{
					final long from = Math.round( input.min( d ) + i * newSize - i * o );
					final long to = from + newSizeInt - 1;

					System.out.println( "block " + (numCenterBlocksInt) + ": " + from + " " + to );
					dimIntervals.add( new ValuePair< Long, Long >( from, to ) );
				}

				final long from = ( input.max( d ) - Math.round( newSize ) + 1 );
				final long to = input.max( d );
	
				System.out.println( "block " + (numCenterBlocksInt + 1) + ": " + from + " " + to );
				dimIntervals.add( new ValuePair< Long, Long >( from, to ) );
			}

			intervalBasis.add( dimIntervals );
		}

		final long[] numIntervals = new long[ input.numDimensions() ];

		for ( int d = 0; d < input.numDimensions(); ++d )
			numIntervals[ d ] = intervalBasis.get( d ).size();

		final LocalizingZeroMinIntervalIterator cursor = new LocalizingZeroMinIntervalIterator( numIntervals );
		final ArrayList< Interval > intervalList = new ArrayList<>();

		final int[] currentInterval = new int[ input.numDimensions() ];

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.localize( currentInterval );

			final long[] min = new long[ input.numDimensions() ];
			final long[] max = new long[ input.numDimensions() ];

			for ( int d = 0; d < input.numDimensions(); ++d )
			{
				final Pair< Long, Long > minMax = intervalBasis.get( d ).get( currentInterval[ d ] );
				min[ d ] = minMax.getA();
				max[ d ] = minMax.getB();
			}

			intervalList.add( new FinalInterval( min, max ) );
		}

		return intervalList;
	}

	public static void main( String[] args )
	{
		Interval input = new FinalInterval( new long[]{ 0 }, new long[] { 1915 - 1 } );
		long[] overlapPx = new long[] { 10 };
		long[] targetSize = new long[] { 500 };

		ArrayList< Interval > intervals = distributeIntervalsFixedOverlap( input, overlapPx, targetSize );

		System.out.println();

		for ( final Interval interval : intervals )
			System.out.println( Util.printInterval( interval ) );
	}
}
