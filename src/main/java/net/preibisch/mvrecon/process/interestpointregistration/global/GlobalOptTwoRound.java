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
package net.preibisch.mvrecon.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import mpicbg.models.Affine3D;
import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.linkremoval.LinkRemovalStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.weak.WeakLinkFactory;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.weak.WeakLinkPointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GlobalOptTwoRound
{
	/**
	 * 
	 * @param model - the transformation model to run the global optimizations on
	 * @param pmc - the pointmatch creator (makes mpicbg PointMatches from anything,
	 * e.g. corresponding interest points or stitching results)
	 * @param csStrong - the Iterative Convergence strategy applied to the strong links,
	 * as created by the pmc
	 * @param lms - decides for the iterative global optimization that is run on the
	 * strong links, which link to drop in an iteration
	 * @param wlf - a factory for creating weak links for the not optimized views.
	 * @param csWeak - the convergence strategy for optimizing the weak links, typically
	 * this is a new ConvergenceStrategy( Double.MAX_VALUE );
	 * @param fixedViews - which views are fixed
	 * @param groupsIn - which views are grouped
	 * @return map from view id to resulting transform
	 * @param <M> mpicbg model type
	 */
	public static < M extends Model< M > > HashMap< ViewId, AffineTransform3D > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy csStrong,
			final LinkRemovalStrategy lms,
			final WeakLinkFactory wlf,
			final ConvergenceStrategy csWeak,
			final Collection< ViewId > fixedViews,
			final Collection< Group< ViewId > > groupsIn )
	{
		return compute( model, pmc, csStrong, lms, null, wlf, csWeak, fixedViews, groupsIn );
	}

	/**
	 * 
	 * @param model - the transformation model to run the global optimizations on
	 * @param pmc - the pointmatch creator (makes mpicbg PointMatches from anything,
	 * e.g. corresponding interest points or stitching results)
	 * @param csStrong - the Iterative Convergence strategy applied to the strong links,
	 * as created by the pmc
	 * @param lms - decides for the iterative global optimization that is run on the
	 * strong links, which link to drop in an iteration
	 * @param removedInconsistentPairs - optional Collection in which pairs that were identified
	 * to be inconsistent and were removed are added (can be null)
	 * @param wlf - a factory for creating weak links for the not optimized views.
	 * @param csWeak - the convergence strategy for optimizing the weak links, typically
	 * this is a new ConvergenceStrategy( Double.MAX_VALUE );
	 * @param fixedViews - which views are fixed
	 * @param groupsIn - which views are grouped
	 * @return map from view id to resulting transform
	 * @param <M> mpicbg model type
	 */
	public static < M extends Model< M > > HashMap< ViewId, AffineTransform3D > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy csStrong,
			final LinkRemovalStrategy lms,
			final Collection< Pair< Group< ViewId >, Group< ViewId > > > removedInconsistentPairs,
			final WeakLinkFactory wlf,
			final ConvergenceStrategy csWeak,
			final Collection< ViewId > fixedViews,
			final Collection< Group< ViewId > > groupsIn )
	{
		// find strong links, run global opt iterative
		final HashMap< ViewId, Tile< M > > models1 = GlobalOptIterative.compute( model, pmc, csStrong, lms, removedInconsistentPairs, fixedViews, groupsIn );

		// identify groups of connected views
		final List< Set< Tile< ? > > > sets = Tile.identifyConnectedGraphs( models1.values() );

		// there is just one connected component -> all views already aligned
		// return first round results
		if ( sets.size() == 1 )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Not more than one group left after first round of global opt (all views are connected), this means we are already done." );

			final HashMap< ViewId, AffineTransform3D > finalRelativeModels = new HashMap<>();

			for ( final ViewId viewId : models1.keySet() )
				finalRelativeModels.put( viewId, TransformationTools.getAffineTransform( (Affine3D< ? >)models1.get( viewId ).getModel() ) );

			return finalRelativeModels;
		}

		// every connected set becomes one group
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Identified the following (dis)connected groups:" );

		final ArrayList< Group< ViewId > > groupsNew = new ArrayList<>();
		for ( final Set< Tile< ? > > connected : sets )
		{
			final Group< ViewId > group = assembleViews( connected, models1 );
			groupsNew.add( group );

			IOFunctions.println( group );
		}

		// compute the weak links using the new groups and the results of the first run
		final WeakLinkPointMatchCreator< M > wlpmc = wlf.create( models1 );

		// run global opt without iterative
		final HashMap< ViewId, Tile< M > > models2 = GlobalOpt.compute( model, wlpmc, csWeak, fixedViews, groupsNew );

		// the combination of models from:
		// the first round of global opt (strong links) + averageMapBack + the second round of global opt (weak links)
		final HashMap< ViewId, AffineTransform3D > finalRelativeModels = new HashMap<>();

		for ( final ViewId viewId : models2.keySet() )
		{
			final AffineTransform3D combined = TransformationTools.getAffineTransform( (Affine3D< ? >)models1.get( viewId ).getModel() );
			combined.preConcatenate( TransformationTools.getAffineTransform( (Affine3D< ? >)models2.get( viewId ).getModel() ) );
	
			finalRelativeModels.put( viewId, combined );
		}

		return finalRelativeModels;
	}

	public static Group< ViewId > assembleViews( final Set< Tile< ? > > set, final HashMap< ViewId, ? extends Tile< ? > > models )
	{
		final Group< ViewId > group = new Group<>();

		for ( final ViewId viewId : models.keySet() )
			if ( set.contains( models.get( viewId ) ) )
				group.getViews().add( viewId );

		return group;
	}
}
