package net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;

public interface PointMatchCreator
{
	/**
	 * @return - all views that this class knows and that are part of the global opt, called first
	 */
	public HashSet< ViewId > getAllViews();

	/**
	 * By default all weights are 1, if wanted one can adjust them here, otherwise simply return.
	 * The idea is to modify the weights in the PointMatchGeneric objects, that are later on just added
	 * called second
	 * 
	 * @param tileMap - the map from viewId to Tile
	 * @param groups - which groups exist
	 * @param fixedViews - which views are fixed (one might need it?)
	 * @param <M> model type
	 */
	public < M extends Model< M > > void assignWeights(
			final HashMap< ViewId, Tile< M > > tileMap,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews );

	/**
	 * assign pointmatches for all views that this object knows and that are present in tileMap.keySet(),
	 * which comes from getAllViews() plus what is in the group definition of the globalopt called last
	 *
	 * @param tileMap - the map from viewId to Tile
	 * @param groups - which groups exist (one might need it?)
	 * @param fixedViews - which views are fixed (one might need it?)
	 * @param <M> model type
	 */
	public < M extends Model< M > > void assignPointMatches(
			final HashMap< ViewId, Tile< M > > tileMap,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews );
}
