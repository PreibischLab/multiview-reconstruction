/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.pointcloud.pointdescriptor.matcher;

public class Quaternion
{
	float w, x, y, z;
	
	public Quaternion( final float w, final float x, final float y, final float z )
	{
		this.w = w;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Quaternion( final float m00, final float m01, final float m02, 
					   final float m10, final float m11, final float m12,
					   final float m20, final float m21, final float m22 )
	{		
        /* find largest value on diagonal */      
        final float max = Math.max( Math.max( m00, m11 ), m22 );        
        final float qUU, qUV, qUW; 
        final float qVU, qVV, qVW;
        final float qWU, qWV, qWW;
        
        if ( max == m00 )
        {
        	/* u = x, v = y, w = z */
        	qUV = m01;
        	qUU = m00;
        	qUW = m02;

        	qVU = m10;
        	qVV = m11;
        	qVW = m12;

        	qWU = m20;        	
        	qWW = m22;
        	qWV = m21;        	
        }
        else if ( max == m11 )
        {
        	/* u = y, v = z, w = x */
        	qUV = m12;
        	qUU = m11;
        	qUW = m10;
        	
        	qVU = m21;
        	qVV = m22;
        	qVW = m20;
        	
        	qWU = m01;
        	qWV = m02;
        	qWW = m00;
        }
        else
        {
        	/* u = z, v = x, w = y */
        	qUU = m22;
        	qUV = m20;
        	qUW = m21;

        	qVU = m02;
        	qVV = m00;
        	qVW = m01;
        	
        	qWU = m12;
        	qWV = m10;
        	qWW = m11;
        }
        	
        final float r = (float)Math.sqrt( 1 + qUU - qVV - qWW );
        w = ( qWV - qVW ) / ( 2*r );
        x = r/2;
        y = ( qUV + qVU ) / ( 2*r );
        z = ( qWU + qUW ) / ( 2*r );
	}	
	
	public float getW() { return w; }
	public float getX() { return x; }
	public float getY() { return y; }
	public float getZ() { return z; }
	
	public String toString()
	{
		return w + " + " + x + "i + " + y + "j + " + z + "k";
	}
}
