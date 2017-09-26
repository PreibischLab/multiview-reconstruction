package net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.HashMap;
import java.util.HashSet;

import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;

public abstract class WeakLinkPointMatchCreator< M extends Model< M > > implements PointMatchCreator
{
	final HashMap< ViewId, Tile< M > > models1;
	final HashSet< ViewId > allViews;

	/**
	 * @param models1 - the models from the first round of global optimization
	 */
	public WeakLinkPointMatchCreator( final HashMap< ViewId, Tile< M > > models1 )
	{
		this.models1 = models1;
		this.allViews = new HashSet<>();

		this.allViews.addAll( models1.keySet() );
	}

	@Override
	public HashSet< ViewId > getAllViews() { return allViews; }
}
