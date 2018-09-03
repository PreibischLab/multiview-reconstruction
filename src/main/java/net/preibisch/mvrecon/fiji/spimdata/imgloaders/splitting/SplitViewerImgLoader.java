package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import java.util.HashMap;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import net.imglib2.Interval;
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

	@Override
	public CacheControl getCacheControl()
	{
		return underlyingImgLoader.getCacheControl();
	}
}
