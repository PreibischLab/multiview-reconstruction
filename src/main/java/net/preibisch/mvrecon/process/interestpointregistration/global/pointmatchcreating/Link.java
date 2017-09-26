package net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineGet;

public class Link<T>
{
	public enum LinkType{ WEAK, STRONG; }

	private final T first;
	private final T second;
	private final AffineGet transform;
	private final LinkType linkType;
	private final RealInterval boundingBox;
	
	public Link(final T fst, final T snd, final RealInterval boundingBox, final AffineGet transform, final LinkType linkType)
	{
		this.first = fst;
		this.second = snd;
		this.transform = transform;
		this.linkType = linkType;
		this.boundingBox = boundingBox;
	}
	
	public RealInterval getBoundingBox() { return boundingBox; }
	public T getFirst() { return first; }
	public T getSecond() { return second; }
	public AffineGet getTransform() { return transform; }
	public LinkType getLinkType() { return linkType; }

	@Override
	public String toString()
	{ return "("+ first.toString() + ", " + second.toString() + ")"; }
	
	public static <T> List<Set<T>> 
			getConnectedComponents(Collection<Link<T>> links, LinkType linkType)
	{
		
		List< Set< T > > connectedNodes = new ArrayList<>();
		
		for(Link<T> l : links)
		{
						
			// merge all components connected by the link if the link is good
			if (l.getLinkType() == linkType){
				
				// put every endpoint in a separate component first
				Set<T> newComponent1 = new HashSet<>();
				newComponent1.add( l.getFirst() );
				connectedNodes.add( newComponent1 );
				
				Set<T> newComponent2 = new HashSet<>();
				newComponent2.add( l.getSecond() );
				connectedNodes.add( newComponent2 );
				
				
				Set<T> first = null;
				for (int i = 0; i< connectedNodes.size(); i++)
				{
					if (connectedNodes.get( i ).contains( l.getFirst() ) || 
							connectedNodes.get( i ).contains( l.getSecond() ))
					{
						if (first == null)
							first = connectedNodes.get( i );
						else
						{
							first.addAll( connectedNodes.get( i ) );
							connectedNodes.remove( i-- );
						}
					}
					
				}				
			}
			else
			{
				// bad link, check if the individual nodes are part of a component already
				// if not, add them to their own component
				boolean firstFound = false;
				boolean secondFound = false;
				for (Set<T> comp : connectedNodes){
					firstFound |= comp.contains( l.getFirst() );
					secondFound |= comp.contains( l.getSecond() );
				}
				if (!firstFound)
				{
					Set<T> newComponent1 = new HashSet<>();
					newComponent1.add( l.getFirst() );
					connectedNodes.add( newComponent1 );
				}
				if (!secondFound)
				{
					Set<T> newComponent2 = new HashSet<>();
					newComponent2.add( l.getSecond() );
					connectedNodes.add( newComponent2 );
				}
					
			}
			
		}				
		
		return connectedNodes;		
	}
	
	public static <T> List<List<Link<T>>> 
		getLinksConnectedComponents(List<Set<T>> components, Collection<Link<T>> links,  LinkType linkType)
	{
		List<List<Link<T>>> res = new ArrayList<>();
		for (Set<T> comp : components){
			List<Link<T>> compList = new ArrayList<>();
			for (Link<T> l : links){
				if (!( l.getLinkType() == linkType ))
					continue;
				if (comp.contains( l.getFirst()) || comp.contains(l.getSecond()) )
				{
					compList.add( l );
				}
			}
			if (compList.size() > 0)
				res.add( compList );
		}
		return res;		
	}
	

	
	public static void main(String[] args)
	{
		List<Link<Integer>> myLinks = new ArrayList<>();
		myLinks.add( new Link<>(1, 2, null, null, LinkType.STRONG) );
		myLinks.add( new Link<>(3, 2, null, null, LinkType.STRONG) );
		myLinks.add( new Link<>(3, 4, null, null, LinkType.STRONG) );
		myLinks.add( new Link<>(5, 6, null, null, LinkType.STRONG) );
		myLinks.add( new Link<>(4, 5, null, null, LinkType.WEAK) );
		
		myLinks.add( new Link<>(7, 8, null, null, LinkType.WEAK) );
		myLinks.add( new Link<>(8, 3, null, null, LinkType.WEAK) );
		
		List< Set< Integer > > connectedComponents = getConnectedComponents( myLinks, LinkType.WEAK );
		
		System.out.println( connectedComponents );
		
		List< List< Link< Integer > > > linksConnectedComponents = getLinksConnectedComponents( connectedComponents, myLinks, LinkType.WEAK );
		
		System.out.println( linksConnectedComponents );
	}

}
