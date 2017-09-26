package net.preibisch.mvrecon.fiji.spimdata;

import java.util.Map;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;

public class EmptyEntity extends NamedEntity
{
	private int id;
	private String name;
	final String tag;

	public EmptyEntity( final int id, final String tag )
	{
		this.id = id;
		this.tag = tag;
		this.name = Integer.toString( id );
	}
	/**
	 * Get the unique id of this entity. This unique id is used for example
	 * as a key in {@link Map}s and for cross-referencing in XML files.
	 *
	 * @return unique id.
	 */
	public int getId()
	{
		return id;
	}

	public String getTag() { return tag; }

	@Override
	public int hashCode()
	{
		return getId();
	}

	public String getName()
	{
		return name;
	}

	protected void setName( final String name )
	{
		this.name = name;
	}

	/**
	 * Two {@link Entity}s are equal if they have the same {@link #getId() and the same tag as both are unknown types of entities}.
	 */
	@Override
	public boolean equals( final Object o )
	{
		if ( o == null )
		{
			return false;
		}
		else if ( getClass().isInstance( o ) )
		{
			if ( ( ( Entity ) o ).getId() == getId()  )
			{
				if ( ((EmptyEntity)o).getTag().equals( getTag() ))
					return true;
				else
					return false;
			}
			else
			{
				return false;
			}
		}
		else
		{
			return false;
		}
	}
}
