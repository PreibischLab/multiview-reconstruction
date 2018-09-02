package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import java.util.HashMap;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import net.imglib2.Interval;

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
}
