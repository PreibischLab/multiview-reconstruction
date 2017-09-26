package net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.HashMap;
import java.util.Map;

import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap.OverlapDetection;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;

public class MetaDataWeakLinkFactory implements WeakLinkFactory
{
	final OverlapDetection< ViewId > overlapDetection;
	final Map< ViewId, ViewRegistration > viewRegistrations;

	public MetaDataWeakLinkFactory(
			final Map< ViewId, ViewRegistration > viewRegistrations,
			final OverlapDetection< ViewId > overlapDetection )
	{
		this.viewRegistrations = viewRegistrations;
		this.overlapDetection = overlapDetection;
	}

	@Override
	public < M extends Model< M > > WeakLinkPointMatchCreator< M > create(
			final HashMap< ViewId, Tile< M > > models )
	{
		return new MetaDataWeakLinkCreator<>( models, overlapDetection, viewRegistrations );
	}

}
