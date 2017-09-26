package net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.HashMap;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;

public interface WeakLinkFactory
{
	public < M extends Model< M > > WeakLinkPointMatchCreator< M > create( final HashMap< ViewId, Tile< M > > models );
}
