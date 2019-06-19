package net.preibisch.mvrecon.fiji.spimdata.imgloaders.splitting;

import java.util.HashMap;

import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import net.imglib2.Interval;

public class SplitMultiResolutionImgLoader implements MultiResolutionImgLoader
{
	final MultiResolutionImgLoader underlyingImgLoader;

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
	private final HashMap< Integer, SplitMultiResolutionSetupImgLoader< ? > > splitSetupImgLoaders;

	public SplitMultiResolutionImgLoader(
			final MultiResolutionImgLoader underlyingImgLoader,
			final HashMap< Integer, Integer > new2oldSetupId,
			final HashMap< Integer, Interval > newSetupId2Interval,
			final SequenceDescription oldSD )
	{
		this.underlyingImgLoader = underlyingImgLoader;
		this.new2oldSetupId = new2oldSetupId;
		this.newSetupId2Interval = newSetupId2Interval;
		this.splitSetupImgLoaders = new HashMap<>();
		this.oldSD = oldSD;
	}

	@Override
	public SplitMultiResolutionSetupImgLoader< ? > getSetupImgLoader( final int setupId )
	{
		return getSplitSetupImgLoader( underlyingImgLoader, new2oldSetupId.get( setupId ), setupId, newSetupId2Interval.get( setupId ) );
	}

	private final synchronized < T > SplitMultiResolutionSetupImgLoader< ? > getSplitSetupImgLoader( final MultiResolutionImgLoader underlyingImgLoader, final int oldSetupId, final int newSetupId, final Interval interval )
	{
		SplitMultiResolutionSetupImgLoader< ? > sil = splitSetupImgLoaders.get( newSetupId );
		if ( sil == null )
		{
			sil = createNewSetupImgLoader( underlyingImgLoader.getSetupImgLoader( oldSetupId ), interval );
			splitSetupImgLoaders.put( newSetupId, sil );
		}
		return sil;
	}

	private final synchronized < T > SplitMultiResolutionSetupImgLoader< ? > createNewSetupImgLoader( final MultiResolutionSetupImgLoader< T > setupImgLoader, final Interval interval )
	{
		return new SplitMultiResolutionSetupImgLoader< T >( setupImgLoader, interval );
	}
}
