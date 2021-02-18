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
package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.io.Serializable;

import mpicbg.models.Point;
import net.imglib2.RealLocalizable;

/**
 * Single interest point, extends mpicbg Point by an id
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class InterestPoint extends Point implements RealLocalizable, Serializable
{
	private static final long serialVersionUID = 5615112297702152070L;

	protected final int id;

	/** Report world coordinates or local coordinates to RealLocalizable */
	protected boolean useW;

	public InterestPoint( final int id, final double[] l )
	{
		this( id, l, true );
	}

	public InterestPoint( final int id, final double[] l, final boolean useW )
	{
		super( l );
		this.id = id;
		this.useW = useW;
	}

	public int getId() { return id; }
	public void setUseW( final boolean useW ) { this.useW = useW; }
	public boolean getUseW() { return useW; }

	@Override
	public int numDimensions() { return l.length; }

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = useW? (float)w[ d ] : (float)l[ d ];
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = useW? w[ d ] : l[ d ];
	}

	@Override
	public float getFloatPosition( final int d ) { return useW? (float)w[ d ] : (float)l[ d ]; }

	@Override
	public double getDoublePosition( final int d ) { return useW? w[ d ] : l[ d ]; }

	public InterestPoint newInstance( final int id, final double[] l ) { return new InterestPoint( id, l ); }

	public InterestPoint duplicate() { return clone(); }

	@Override
	public InterestPoint clone() { return new InterestPoint( this.id, this.l ); }

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result + ( useW ? 1231 : 1237 );
		return result;
	}

	@Override
	public boolean equals( Object obj )
	{
		if ( this == obj )
			return true;
		if ( obj == null )
			return false;
		if ( getClass() != obj.getClass() )
			return false;
		final InterestPoint other = (InterestPoint) obj;

		if ( other.id != id )
			return false;

		for ( int d = 0; d < numDimensions(); ++d )
			if ( other.getDoublePosition( d ) != getDoublePosition( d ) )
				return false;

		return true;
	}

}
