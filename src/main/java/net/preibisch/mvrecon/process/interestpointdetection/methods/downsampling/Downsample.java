package net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.ImageJ;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.iterator.ZeroMinIntervalIterator;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.ImagePortion;

public class Downsample
{
	public static < T extends RealType< T > > RandomAccessibleInterval< T > simple2x( final RandomAccessibleInterval<T> input, final ImgFactory< T > imgFactory )
	{
		final boolean[] downsampleInDim = new boolean[ input.numDimensions() ];

		for ( int d = 0; d < downsampleInDim.length; ++d )
			downsampleInDim[ d ] = true;

		return simple2x( input, imgFactory, downsampleInDim );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< T > simple2x( final RandomAccessibleInterval<T> input, final ImgFactory< T > imgFactory, final boolean[] downsampleInDim )
	{
		RandomAccessibleInterval< T > src = input;

		for ( int d = 0; d < input.numDimensions(); ++d )
			if ( downsampleInDim[ d ] )
			{
				final long dim[] = new long[ input.numDimensions() ];

				for ( int e = 0; e < input.numDimensions(); ++e )
				{
					if ( e == d )
						dim[ e ] = src.dimension( e ) / 2;
					else
						dim[ e ] = src.dimension( e );
				}

				final Img< T > img = imgFactory.create( dim, Views.iterable( input ).firstElement() );
				simple2x( src, img, d );
				src = img;
			}

		return src;
	}

	public static < T extends RealType< T > > void simple2x( final RandomAccessibleInterval<T> input, final RandomAccessibleInterval<T> output, final int d )
	{
		final int n = input.numDimensions();

		// iterate all dimensions but the one we are processing int
		final long[] iterateD = new long[ n ];
		long numLines = 1;

		for ( int e = 0; e < n; ++e )
		{
			if ( e == d )
				iterateD[ e ] = 1;
			else
				iterateD[ e ] = output.dimension( e );
			
			numLines *= iterateD[ e ];
		}
		
		//final IterableInterval< T > iterable = new ZeroMinIntervalIterator( iterateD );
				//Views.iterable( Views.hyperSlice( Views.zeroMin( output ), d, 0 ) );

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( numLines );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >() 
			{
				@Override
				public Void call() throws Exception
				{
					final long[] pos = new long[ n ];
					final IntervalIterator cursorDim = new ZeroMinIntervalIterator( iterateD );
					final RandomAccess< T > in = Views.zeroMin( input ).randomAccess();
					final RandomAccess< T > out = Views.zeroMin( output ).randomAccess();
					final long size = output.dimension( d ) - 1;

					cursorDim.jumpFwd( portion.getStartPosition() );

					for ( long j = 0; j < portion.getLoopSize(); ++j )
					{
						cursorDim.fwd();
						cursorDim.localize( pos );

						out.setPosition( pos );

						// the first pixel (avoid outofbounds)
						in.setPosition( pos );
						double v0, v1, v2;

						v1 = in.get().getRealDouble();
						in.fwd( d );
						v0 = v2 = in.get().getRealDouble();
						out.get().setReal( ( v1 + v2 * 0.5 ) / 1.5 );

						// other pixels
						for ( int p = 1; p < size; ++p )
						{
							v0 = v2;
							in.fwd( d );
							v1 = in.get().getRealDouble();
							in.fwd( d );
							v2 = in.get().getRealDouble();
							out.fwd( d );
							out.get().setReal( ( v0 * 0.5 + v1 + v2 * 0.5 ) / 2.0 );
						}

						// last pixel
						in.fwd( d );
						v1 = in.get().getRealDouble();
						out.fwd( d );
						out.get().setReal( ( v1 + v2 * 0.5 ) / 1.5 );
					}
					return null;
				}
			});
		}

		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to compute downsampling: " + e );
			e.printStackTrace();
			return;
		}

		taskExecutor.shutdown();
		
		return;
	}

	public static void main( String[] args )
	{
		final Img< FloatType > img;
		
		//img = OpenImg.open( "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/img_Angle0.tif", new ArrayImgFactory< FloatType >() );
		img = new ArrayImgFactory< FloatType >().create( new long[]{ 515,  231, 15 }, new FloatType() );
		
		final Cursor< FloatType > c = img.localizingCursor();
		
		while ( c.hasNext() )
		{
			c.next().set( c.getIntPosition( 0 ) % 10 + c.getIntPosition( 1 ) % 13 + c.getIntPosition( 2 ) % 3 );
		}
		
		new ImageJ();
		ImageJFunctions.show( img );
		ImageJFunctions.show( simple2x( img, img.factory(), new boolean[]{ true, true, true } ) );
	}
}
