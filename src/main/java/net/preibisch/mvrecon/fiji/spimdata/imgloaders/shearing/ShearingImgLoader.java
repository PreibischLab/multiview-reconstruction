package net.preibisch.mvrecon.fiji.spimdata.imgloaders.shearing;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.janelia.saalfeldlab.n5.N5Exception;

import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.n5.N5ImageLoader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.util.Cast;

public class ShearingImgLoader implements ViewerImgLoader, MultiResolutionImgLoader
{
	/**
	 * underlying image loader
	 */
	final N5ImageLoader n5loader;

	/**
	 * Remembers instances of SplitSetupImgLoader
	 */
	private final HashMap< Integer, ShearingSetupImgLoader<?,?> > shearingSetupImgLoaders;

	/**
	 * Its own cell cache
	 */
	protected VolatileGlobalCellCache cache;

	/**
	 * Only necessary for the cache, and N5ImageLoader does not expose it
	 */
	private final AbstractSequenceDescription< ?, ?, ? > seq;

	private int requestedNumFetcherThreads = -1;
	private boolean isOpen = false;

	public ShearingImgLoader( final AbstractSequenceDescription< ?, ?, ? > seq, final N5ImageLoader n5loader )
	{
		this.seq = seq;
		this.n5loader = n5loader;
		this.shearingSetupImgLoaders = new HashMap<>();
	}

	@Override
	public ShearingSetupImgLoader<?, ?> getSetupImgLoader( final int setupId )
	{
		open();
		return shearingSetupImgLoaders.get( setupId );
	}

	@Override
	public synchronized void setNumFetcherThreads( final int n )
	{
		requestedNumFetcherThreads = n;
		n5loader.setNumFetcherThreads( n );
	}

	public Future< Void > prefetch( final int parallelism )
	{
		return n5loader.prefetch( parallelism );
	}

	private void open()
	{
		if ( !isOpen )
		{
			synchronized ( this )
			{
				if ( isOpen )
					return;

				isOpen = true;

				try
				{
					int maxNumLevels = 1;
					final List< ? extends BasicViewSetup > setups = seq.getViewSetupsOrdered();
					for ( final BasicViewSetup setup : setups )
					{
						final int setupId = setup.getId();
						final ShearingSetupImgLoader< ?, ? > setupImgLoader = createSetupImgLoader( setupId );
						shearingSetupImgLoaders.put( setupId, setupImgLoader );
	
						final double[][] resolutions = n5loader.getSetupImgLoader( setup.getId() ).getMipmapResolutions();
	
						if ( resolutions.length > maxNumLevels )
							maxNumLevels = resolutions.length;
					}
	
					final int numFetcherThreads = ( requestedNumFetcherThreads > 0 ) ? requestedNumFetcherThreads : Runtime.getRuntime().availableProcessors();
					final BlockingFetchQueues< Callable< ? > > queue = new BlockingFetchQueues<>( maxNumLevels, numFetcherThreads );
					cache = new VolatileGlobalCellCache( queue );
				}
				catch ( final IOException e )
				{
					throw new RuntimeException( e );
				}
			}
		}
	}

	private ShearingSetupImgLoader<?, ?> createSetupImgLoader( final int setupId ) throws IOException
	{
		try
		{
			return new ShearingSetupImgLoader<>( setupId, Cast.unchecked( n5loader.getSetupImgLoader( setupId ) ) );
		}
		catch ( final N5Exception e )
		{
			throw new IOException( e );
		}
	}

	@Override
	public CacheControl getCacheControl()
	{
		open();
		return cache;
	}

}
