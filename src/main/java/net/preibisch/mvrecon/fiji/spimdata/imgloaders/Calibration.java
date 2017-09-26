package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

public class Calibration
{
	final int w, h, d;
	final double calX, calY, calZ;
	
	public Calibration( final int w, final int h, final int d, final double calX, final double calY, final double calZ )
	{
		this.w = w;
		this.h = h;
		this.d = d;
		this.calX = calX;
		this.calY = calY;
		this.calZ = calZ;
	}
	
	public double getCalX() { return calX; }
	public double getCalY() { return calY; }
	public double getCalZ() { return calZ; }
	public int getWidth() { return w; }
	public int getHeight() { return h; }
	public int getDepth() { return d; }
}
