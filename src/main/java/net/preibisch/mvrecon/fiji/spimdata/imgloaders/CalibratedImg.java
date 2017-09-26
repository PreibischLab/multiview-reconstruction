package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import net.imglib2.img.Img;

public class CalibratedImg< T > 
{
	final Img< T > image;
	final double calX, calY, calZ;
	
	public CalibratedImg( final Img< T > image )
	{
		this.image = image;
		this.calX = this.calY = this.calZ = -1;
	}
	
	public CalibratedImg( final Img< T > image, final double calX, final double calY, final double calZ )
	{
		this.image = image;
		this.calX = calX;
		this.calY = calY;
		this.calZ = calZ;
	}
	
	public Img< T > getImg(){ return image; }
	public double getCalX() { return calX; }
	public double getCalY() { return calY; }
	public double getCalZ() { return calZ; }
}
