/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import net.imglib2.Interval;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

public class SplitViewerImgLoader implements ViewerImgLoader, MultiResolutionImgLoader
{
	final ViewerImgLoader underlyingImgLoader;

	/**
	 * Maps the newly assigned ViewSetupId to the old ViewSetupId
	 */
	final HashMap< Integer, Integer > new2oldSetupId;

	/**
	 * Maps the newly assigned ViewSetupId to Interval inside the old ViewSetupId
	 */
	final HashMap< Integer, Interval > newSetupId2Interval;

	/**
	 * The old SequenceDescription is be needed for the underlying imgloader
	 */
	final SequenceDescription oldSD;

	/**
	 * Remembers instances of SplitSetupImgLoader
	 */
	private final HashMap< Integer, SplitViewerSetupImgLoader > splitSetupImgLoaders;

	/**
	 * Its own cell cache
	 */
	protected VolatileGlobalCellCache cache;

	public SplitViewerImgLoader(
			final ViewerImgLoader underlyingImgLoader,
			final HashMap< Integer, Integer > new2oldSetupId,
			final HashMap< Integer, Interval > newSetupId2Interval,
			final SequenceDescription oldSD )
	{
		this.underlyingImgLoader = underlyingImgLoader;
		this.new2oldSetupId = new2oldSetupId;
		this.newSetupId2Interval = newSetupId2Interval;
		this.oldSD = oldSD;
		this.splitSetupImgLoaders = new HashMap<>();
	}

	private boolean isOpen = false;

	@Override
	public SplitViewerSetupImgLoader getSetupImgLoader( final int setupId )
	{
		return getSplitViewerSetupImgLoader( underlyingImgLoader, new2oldSetupId.get( setupId ), setupId, newSetupId2Interval.get( setupId ) );
	}

	private final synchronized SplitViewerSetupImgLoader getSplitViewerSetupImgLoader( final ViewerImgLoader underlyingImgLoader, final int oldSetupId, final int newSetupId, final Interval interval )
	{
		SplitViewerSetupImgLoader sil = splitSetupImgLoaders.get( newSetupId );
		if ( sil == null )
		{
			final ViewerSetupImgLoader setupLoader = underlyingImgLoader.getSetupImgLoader( oldSetupId );
			final Object imgType = setupLoader.getImageType();
			final Object volTyoe = setupLoader.getVolatileImageType();

			if ( !imgType.getClass().isInstance( new UnsignedShortType() ) || !volTyoe.getClass().isInstance( new VolatileUnsignedShortType() ) )
				throw new RuntimeException( "The underlying ViewerSetupImgLoader is not typed for <UnsignedShortType, VolatileUnsignedShortType>, cannot split up for BDV." );

			sil = createNewSetupImgLoader( (ViewerSetupImgLoader)underlyingImgLoader.getSetupImgLoader( oldSetupId ), interval );
			splitSetupImgLoaders.put( newSetupId, sil );
		}
		return sil;
	}

	private final synchronized SplitViewerSetupImgLoader createNewSetupImgLoader(
			final ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > setupImgLoader,
			final Interval interval )
	{
		return new SplitViewerSetupImgLoader( setupImgLoader, interval );
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

				int maxNumLevels = 1;
				final List< ? extends BasicViewSetup > setups = oldSD.getViewSetupsOrdered();
				for ( final BasicViewSetup setup : setups )
				{
					final double[][] resolutions = underlyingImgLoader.getSetupImgLoader( setup.getId() ).getMipmapResolutions();

					if ( resolutions.length > maxNumLevels )
						maxNumLevels = resolutions.length;
				}

				final int numFetcherThreads = 1;
				final BlockingFetchQueues< Callable< ? > > queue = new BlockingFetchQueues<>( maxNumLevels, numFetcherThreads );
				cache = new VolatileGlobalCellCache( queue );
			}
		}
	}

	@Override
	public CacheControl getCacheControl()
	{
		open();
		return cache;
	}
}
