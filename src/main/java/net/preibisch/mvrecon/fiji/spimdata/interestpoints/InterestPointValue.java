package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

public class InterestPointValue extends InterestPoint
{
	private static final long serialVersionUID = -4538307380458133556L;

	final private double intensity;

	public InterestPointValue( final int id, final double[] l, final double intensity )
	{
		super( id, l );
		this.intensity = intensity;
	}

	public double getIntensity() { return intensity; }
}
