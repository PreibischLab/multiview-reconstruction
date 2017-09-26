package mpicbg.pointdescriptor.test;

import mpicbg.models.Point;
import net.imglib2.RealLocalizable;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

public class VirtualPointNode<P extends Point> implements RealLocalizable
{

	final P p;
	final int numDimensions;
	final boolean useW;
	
	public VirtualPointNode( final P p )
	{
		this.useW = true;
		this.p = p;
		this.numDimensions = p.getL().length;
	}
	
	public P getPoint() { return p; }

	@Override
	public int numDimensions() { return numDimensions; }

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < position.length; ++d )
			position[ d ] = useW? (float)p.getW()[ d ] : (float)p.getL()[ d ];
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < position.length; ++d )
			position[ d ] = useW? p.getW()[ d ] : p.getL()[ d ];
	}

	@Override
	public float getFloatPosition( final int d ) { return useW? (float)p.getW()[ d ] : (float)p.getL()[ d ]; }

	@Override
	public double getDoublePosition( final int d ) { return useW? p.getW()[ d ] : p.getL()[ d ]; }

	public InterestPoint newInstance( final int id, final double[] l ) { return new InterestPoint( id, l ); }
}
