package net.preibisch.mvrecon.process.fusion.transformed.nonrigid;

public class SimpleReferenceIP implements NonrigidIP
{
	final double[] l, w;
	double[] targetW;

	public SimpleReferenceIP( final double[] l, final double[] w, final double[] targetW )
	{
		this.l = l;
		this.w = w;
		this.targetW = targetW;
	}

	public SimpleReferenceIP( final double[] l, final double[] w )
	{
		this( l, w, w );
	}

	public void setTargetW( final double[] targetW ) { this.targetW = targetW; }
	public double[] getTargetW() { return targetW; }
	public double[] getL() { return l; }
	public double[] getW() { return w; }
}
