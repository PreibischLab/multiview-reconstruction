package net.preibisch.stitcher.algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimDataTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class SpimDataFilteringAndGroupingFunctions< AS extends AbstractSpimData< ? > > {

	public List<? extends BasicViewDescription< ? > > getFilteredViews()
	{ 
		return SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), filters);
	}

	public List< Group< BasicViewDescription< ?  > >> getGroupedViews(boolean filtered)
	{
		final List<BasicViewDescription< ? > > ungroupedElements =
				SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), filtered? filters : new HashMap<>(), false );
		return Group.combineBy( ungroupedElements, groupingFactors);
	}

	public List<Pair<? extends Group< ? extends BasicViewDescription< ? extends BasicViewSetup > >, ? extends Group< ? extends BasicViewDescription< ? extends BasicViewSetup >>>> getComparisons()
	{
		final List<Pair<? extends Group< ? extends BasicViewDescription< ? extends BasicViewSetup > >, ? extends Group< ? extends BasicViewDescription< ? extends BasicViewSetup >>>> res = new ArrayList<>();
		
		// filter first
		final List<BasicViewDescription< ? > > ungroupedElements =
				SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), filters);
		// then group
		final List< Group< BasicViewDescription< ?  > >> groupedElements = 
				Group.combineBy(ungroupedElements, groupingFactors);
		
		// go through possible group pairs
		for (int i = 0; i < groupedElements.size(); ++i)
			for(int j = i+1; j < groupedElements.size(); ++j)
			{
				// we will want to process the pair if:
				// the groups do not differ along an axis along which we want to treat elements individually (e.g. Angle)
				// but they differ along an axis that we want to register (e.g Tile)
				if (!groupsDifferByAny( groupedElements.get( i ), groupedElements.get( j ), axesOfApplication ) 
						&& groupsDifferByAny( groupedElements.get( i ), groupedElements.get( j ), axesOfComparison ))
					res.add(new ValuePair<>(groupedElements.get( i ), groupedElements.get( j )));
			}
		return res;
	}

	public boolean getDialogWasCancelled()
	{
		return dialogWasCancelled;
	}

	
	public static List<Class<? extends Entity>> entityClasses = new ArrayList<>();
	static 
	{
		entityClasses.add( TimePoint.class );
		entityClasses.add( Channel.class );
		entityClasses.add( Illumination.class );
		entityClasses.add( Angle.class );
		entityClasses.add( Tile.class );
	}

	Set<Class<? extends Entity>> groupingFactors; // attributes by which views are grouped first
	Set<Class<? extends Entity>> axesOfApplication; // axes of application -> we want to process within single instances (e.g. each TimePoint separately)
	Set<Class<? extends Entity>> axesOfComparison; // axes we want to compare
	Map<Class<? extends Entity>, List<? extends Entity>> filters; // filters for the different attributes
	AS data;
	GroupedViewAggregator gva;

	public boolean requestExpertSettingsForGlobalOpt = true;
	boolean dialogWasCancelled = false;

	public SpimDataFilteringAndGroupingFunctions(AS data)
	{
		groupingFactors = new HashSet<>();
		axesOfApplication = new HashSet<>();
		axesOfComparison = new HashSet<>();
		filters = new HashMap<>();
		this.data = data;
		gva = new GroupedViewAggregator();
	}

	public AS getSpimData()
	{
		return data;
	}

	public GroupedViewAggregator getGroupedViewAggregator()
	{
		return gva;
	}

	public void addGroupingFactor(Class<? extends Entity> factor ) {
		groupingFactors.add(factor);
	}

	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances){
		filters.put(cl, instances);
	}

	public void addFilters(Collection<? extends BasicViewDescription< ? extends BasicViewSetup >> selected)
	{
		for (Class<? extends Entity> cl : entityClasses)
			filters.put( cl, new ArrayList<>(getInstancesOfAttribute( selected, cl ) ) );
	}

	public void addApplicationAxis(Class<? extends Entity> axis ) {
		axesOfApplication.add(axis);
	}

	public void addComparisonAxis(Class<? extends Entity> axis ) {
		axesOfComparison.add(axis);
	}

	public void clearGroupingFactors()
	{
		groupingFactors.clear();
	}

	public void clearFilters()
	{
		filters.clear();
	}

	public void clearApplicationAxes()
	{
		axesOfApplication.clear();
	}

	public void clearComparisonAxes()
	{
		axesOfComparison.clear();
	}

	public Set< Class< ? extends Entity > > getGroupingFactors()
	{
		return groupingFactors;
	}

	public Set< Class< ? extends Entity > > getAxesOfApplication()
	{
		return axesOfApplication;
	}

	public Set< Class< ? extends Entity > > getAxesOfComparison()
	{
		return axesOfComparison;
	}

	public Map< Class< ? extends Entity >, List< ? extends Entity > > getFilters()
	{
		return filters;
	}
	
	protected static boolean groupsDifferByAny(Iterable< BasicViewDescription< ?  > > vds1, Iterable< BasicViewDescription< ?  > > vds2, Set<Class<? extends Entity>> entities)
	{
		for (Class<? extends Entity> entity : entities)
		{
			for ( BasicViewDescription< ?  > vd1 : vds1)
				for ( BasicViewDescription< ?  > vd2 : vds2)
				{
					if (entity == TimePoint.class)
					{
						if (!vd1.getTimePoint().equals( vd2.getTimePoint() ))
							return true;
					}
					else
					{
						if (!vd1.getViewSetup().getAttribute( entity ).equals( vd2.getViewSetup().getAttribute( entity ) ) )
							return true;
					}
					
				}
		}
		
		return false;
	}


	/**
	 * get all instances of the attribute class cl in the (grouped) views vds. 
	 * @param vds collection of view description lists
	 * @param cl Class of entity to get instances of
	 * @return all instances of cl in the views in vds
	 */
	public static Set<Entity> getInstancesOfAttributeGrouped(Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > vds, Class<? extends Entity> cl)
	{	
		// make one List out of the nested Collection and getInstancesOfAttribute
		return getInstancesOfAttribute( vds.stream().reduce( new ArrayList<>(), (x, y) -> {x.addAll(y); return x;} ), cl );
	}

	public static Set<Entity> getInstancesOfAttribute(Collection<? extends BasicViewDescription< ? extends BasicViewSetup >> vds, Class<? extends Entity> cl)
	{
		Set<Entity> res = new HashSet<>();
		for (BasicViewDescription< ? extends BasicViewSetup > vd : vds)
			if (cl == TimePoint.class)
				res.add( vd.getTimePoint() );
			else
				res.add( vd.getViewSetup().getAttribute( cl ) );
		return res;
	}

	/**
	 * get the instances of class cl that are present in at least one ViewDescription of each of the groups in vds.
	 * @param vds collection of view description iterables
	 * @param cl Class of entity to get instances of
	 * @return all instances of cl in the views in vds
	 */
	public static List<? extends Entity> getInstancesInAllGroups(Collection< ? extends Iterable< BasicViewDescription< ? extends BasicViewSetup > > > vds, Class<? extends Entity> cl)
	{
		Set<Entity> res = new HashSet<>();
		Iterator< ? extends Iterable< BasicViewDescription< ? extends BasicViewSetup > > > it = vds.iterator();
		for (BasicViewDescription< ? extends BasicViewSetup > vd : it.next())
			if (cl == TimePoint.class)
				res.add( vd.getTimePoint() );
			else
				res.add( vd.getViewSetup().getAttribute( cl ) );

		while (it.hasNext()) 
		{
			Iterable< BasicViewDescription< ? extends BasicViewSetup > > vdli = it.next();
			Set<Entity> resI = new HashSet<>();
			for (BasicViewDescription< ? extends BasicViewSetup > vd : vdli)
				if (cl == TimePoint.class)
					resI.add( vd.getTimePoint() );
				else
					resI.add( vd.getViewSetup().getAttribute( cl ) );

			Set<Entity> newRes = new HashSet<>();
			for (Entity e : resI)
			{
				if(res.contains( e ))
					newRes.add( e );
			}
			res = newRes;
		}

		return new ArrayList<>(res);
	}
}
