package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.util.HashMap;
import java.util.Map;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.preibisch.mvrecon.fiji.datasetmanager.SmartSPIM.SmartSPIMMetaData;

public class SmartSPIMImgLoader implements ImgLoader
{
	final SmartSPIMMetaData metadata;
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;;

	/**
	 * Maps setup id to {@link SetupImgLoader}.
	 */
	private final Map< Integer, SmartSPIMSetupImgLoader > setupImgLoaders = new HashMap<>();

	public SmartSPIMImgLoader(
			final SmartSPIMMetaData metadata,
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription )
	{
		this.metadata = metadata;
		this.sequenceDescription = sequenceDescription;
	}

	@Override
	public SetupImgLoader<?> getSetupImgLoader( final int setupId )
	{
		return open( setupId );
	}

	private SetupImgLoader< UnsignedShortType > open( final int setupId )
	{
		if ( !setupImgLoaders.containsKey( setupId ) )
		{
			synchronized ( this )
			{
				if ( !setupImgLoaders.containsKey( setupId ) )
				{
					setupImgLoaders.put( setupId, new SmartSPIMSetupImgLoader( setupId, metadata, sequenceDescription ) );
				}
			}
		}

		return setupImgLoaders.get( setupId );
	}

}
