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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.mpicbg.PointMatchGeneric;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;

public class MatcherPairwiseTools
{
	public static < V > HashSet< V > allViews( final Group< ? extends V > a, final Group< ? extends V > b )
	{
		final HashSet< V > all = new HashSet<>();
		all.addAll( a.getViews() );
		all.addAll( b.getViews() );

		return all;
	}

	public static < V extends ViewId > Map< V, List< CorrespondingInterestPoints > > clearCorrespondences(
			final Collection< V > viewIds,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap )
	{
		final Map< V, InterestPointList > map = new HashMap<>();

		for ( final V view : viewIds )
			map.put( view, interestpoints.get( view ).getInterestPointList( labelMap.get( view ) ) );

		return clearCorrespondences( map );
	}

	public static < V extends ViewId > Map< V, List< CorrespondingInterestPoints > > clearCorrespondences( final Map< V, ? extends InterestPointList > map )
	{
		final Map< V, List< CorrespondingInterestPoints > > cMap = new HashMap<>();

		for ( final V viewId : map.keySet() )
		{
			final InterestPointList list = map.get( viewId );
			final ArrayList< CorrespondingInterestPoints > cList = new ArrayList<>();
			list.setCorrespondingInterestPoints( cList );
			cMap.put( viewId, cList );
		}

		return cMap;
	}

	public static < V extends ViewId, P extends PairwiseResult< GroupedInterestPoint< V > > >
		List< Pair< Pair< V, V >, PairwiseResult< GroupedInterestPoint< V > > > >
			addCorrespondencesFromGroups(
			final Collection< ? extends Pair< ?, P > > resultGroup,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap,
			final Map< V, ? extends List< CorrespondingInterestPoints > > cMap
			)
	{
		// we transform HashMap< Pair< Group < V >, Group< V > >, PairwiseResult > to HashMap< Pair< V, V >, PairwiseResult >
		final HashMap< Pair< V, V >, PairwiseResult< GroupedInterestPoint< V > > > transformedMap = new HashMap<>();

		// it doesn't matter which pair of groups it comes from: ?
		for ( final Pair< ?, P > p : resultGroup )
		{
			for ( final PointMatchGeneric< GroupedInterestPoint< V > > pm : p.getB().getInliers() )
			{
				// assign correspondences
				final GroupedInterestPoint< V > gpA = pm.getPoint1();
				final GroupedInterestPoint< V > gpB = pm.getPoint2();

				final V viewIdA = gpA.getV();
				final V viewIdB = gpB.getV();

				if ( p.getB().storeCorrespondences() )
				{
					final String labelA = labelMap.get( viewIdA );
					final String labelB = labelMap.get( viewIdB );
	
					final CorrespondingInterestPoints correspondingToA = new CorrespondingInterestPoints( gpA.getId(), viewIdB, labelB, gpB.getId() );
					final CorrespondingInterestPoints correspondingToB = new CorrespondingInterestPoints( gpB.getId(), viewIdA, labelA, gpA.getId() );
	
					cMap.get( viewIdA ).add( correspondingToA );
					cMap.get( viewIdB ).add( correspondingToB );
				}

				// update transformedMap
				final Pair< V, V > pair = new ValuePair<>( viewIdA, viewIdB );

				final PairwiseResult< GroupedInterestPoint< V > > pwr;

				if ( transformedMap.containsKey( pair ) )
					pwr = transformedMap.get( pair );
				else
				{
					pwr = new PairwiseResult<>( p.getB().storeCorrespondences() );
					pwr.setInliers( new ArrayList<>(), p.getB().getError() );
					pwr.setCandidates( new ArrayList<>() );
					transformedMap.put( pair, pwr );
				}

				pwr.getInliers().add( pm );
			}

			for ( final PointMatchGeneric< GroupedInterestPoint< V > > pm : p.getB().getCandidates() )
			{
				// assign correspondences
				final GroupedInterestPoint< V > gpA = pm.getPoint1();
				final GroupedInterestPoint< V > gpB = pm.getPoint2();

				final V viewIdA = gpA.getV();
				final V viewIdB = gpB.getV();

				// update transformedMap
				final Pair< V, V > pair = new ValuePair<>( viewIdA, viewIdB );

				final PairwiseResult< GroupedInterestPoint< V > > pwr;

				if ( transformedMap.containsKey( pair ) )
					pwr = transformedMap.get( pair );
				else
				{
					pwr = new PairwiseResult<>( p.getB().storeCorrespondences() );
					pwr.setInliers( new ArrayList<>(), p.getB().getError() );
					pwr.setCandidates( new ArrayList<>() );
					transformedMap.put( pair, pwr );
				}

				pwr.getCandidates().add( pm );
			}

		}

		for ( final V viewId : cMap.keySet() )
			interestpoints.get( viewId ).getInterestPointList( labelMap.get( viewId ) ).setCorrespondingInterestPoints( cMap.get( viewId ) );

		final ArrayList< Pair< Pair< V, V >, PairwiseResult< GroupedInterestPoint< V > > > > transformedList = new ArrayList<>();

		for ( final Pair< V, V > pair : transformedMap.keySet() )
			transformedList.add( new ValuePair<>( pair, transformedMap.get( pair ) ) );

		return transformedList;
	}

	/*
	 * Add correspondences to the interestpointlists
	 */
	public static < I extends InterestPoint > void addCorrespondences(
			final List< PointMatchGeneric< I > > correspondences,
			final ViewId viewIdA,
			final ViewId viewIdB,
			final String labelA,
			final String labelB,
			final InterestPointList listA,
			final InterestPointList listB )
	{
		final List< CorrespondingInterestPoints > corrListA = listA.getCorrespondingInterestPointsCopy();
		final List< CorrespondingInterestPoints > corrListB = listB.getCorrespondingInterestPointsCopy();

		for ( final PointMatchGeneric< I > pm : correspondences )
		{
			final I pA = pm.getPoint1();
			final I pB = pm.getPoint2();

			final CorrespondingInterestPoints correspondingToA = new CorrespondingInterestPoints( pA.getId(), viewIdB, labelB, pB.getId() );
			final CorrespondingInterestPoints correspondingToB = new CorrespondingInterestPoints( pB.getId(), viewIdA, labelA, pA.getId() );

			corrListA.add( correspondingToA );
			corrListB.add( correspondingToB );
		}

		listA.setCorrespondingInterestPoints( corrListA );
		listB.setCorrespondingInterestPoints( corrListB );
	}

	public static void assignLoggingDescriptions(
			final Pair< ?, ? > p, PairwiseResult< ? > pwr )
	{
		if ( ViewId.class.isInstance( p.getA() ) && ViewId.class.isInstance( p.getB() ) )
		{
			final String description =
					"[TP=" + ((ViewId)p.getA()).getTimePointId() +
					" ViewId=" + ((ViewId)p.getA()).getViewSetupId() +
					" >>> TP=" + ((ViewId)p.getB()).getTimePointId() +
					" ViewId=" + ((ViewId)p.getB()).getViewSetupId() + "]";

			pwr.setDescription( description );
		}
		else if ( Group.class.isInstance( p.getA() ) && Group.class.isInstance( p.getB() ) )
		{
			pwr.setDescription( "[Group {" + p.getA() + "} >>> Group {" + p.getB() + "}]" );
		}
		else
		{
			pwr.setDescription( "[" + p.getA() + " >>> " + p.getB() + "]" );
		}
	}

	public static < V, I extends InterestPoint > List< Pair< Pair< V, V >, PairwiseResult< I > > > computePairs(
			final List< Pair< V, V > > pairs,
			final Map< V, List< I > > interestpoints,
			final MatcherPairwise< I > matcher )
	{
		return computePairs( pairs, interestpoints, matcher, null );
	}

	public static < V, I extends InterestPoint > List< Pair< Pair< V, V >, PairwiseResult< I > > > computePairs(
			final List< Pair< V, V > > pairs,
			final Map< V, ? extends List< I > > interestpoints,
			final MatcherPairwise< I > matcher,
			final ExecutorService exec )
	{
		final ExecutorService taskExecutor;
		
		if ( exec == null )
			taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		else
			taskExecutor = exec;

		final ArrayList< Callable< PairwiseResult< I > > > tasks = new ArrayList<>(); // your tasks

		for ( final Pair< V, V > pair : pairs )
		{
			final List< I > listA, listB;

			if ( matcher.requiresInterestPointDuplication() )
			{
				listA = new ArrayList<>();
				listB = new ArrayList<>();

				for ( final I ip : interestpoints.get( pair.getA() ) )
					listA.add( (I)ip.clone() );

				for ( final I ip : interestpoints.get( pair.getB() ) )
					listB.add( (I)ip.clone() );
			}
			else
			{
				listA = interestpoints.get( pair.getA() );
				listB = interestpoints.get( pair.getB() );
			}

			tasks.add( new Callable< PairwiseResult< I > >()
			{
				@Override
				public PairwiseResult< I > call() throws Exception
				{
					final PairwiseResult< I > pwr = matcher.match( listA, listB );
					assignLoggingDescriptions( pair, pwr );
					return pwr;
				}
			});
		}

		final List< Pair< Pair< V, V >, PairwiseResult< I > > > r = new ArrayList<>();

		try
		{
			// invokeAll() returns when all tasks are complete
			List< Future< PairwiseResult< I > > > futures = taskExecutor.invokeAll( tasks );

			for ( int i = 0; i < pairs.size(); ++i )
			{
				final PairwiseResult< I > pwr = futures.get( i ).get();
				final Pair< V, V > pair = pairs.get( i );
				r.add( new ValuePair< Pair< V, V >, PairwiseResult< I > >( pair, pwr ) );
			}
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}

		if ( exec == null )
			taskExecutor.shutdown();

		// TODO:
		return r;
	}
}
