package mpicbg.spim.mpicbg;

import net.preibisch.mvrecon.vecmath.Point3d;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.NoninvertibleModelException;

final public class Java3d
{
	final public static void applyInPlace( final AbstractAffineModel3D<?> m, final Point3d p )
	{
		final double[] tmp = new double[ 3 ];
		
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}

	final public static void applyInPlace( final AbstractAffineModel3D<?> m, final Point3d p, final double[] tmp )
	{
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}
	
	final public static void applyInverseInPlace( final AbstractAffineModel3D<?> m, final Point3d p ) throws NoninvertibleModelException
	{
		final double[] tmp = new double[ 3 ];
		
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInverseInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}
	
	final public static void applyInverseInPlace( final AbstractAffineModel3D<?> m, final Point3d p, final double[] tmp ) throws NoninvertibleModelException
	{
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInverseInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}
	
}
