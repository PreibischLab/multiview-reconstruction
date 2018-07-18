package net.preibisch.mvrecon.process.export;

public interface Calibrateable
{
	public void setCalibration( final double pixelSize, final String unit );
	public String getUnit();
	public double getPixelSize();
}
