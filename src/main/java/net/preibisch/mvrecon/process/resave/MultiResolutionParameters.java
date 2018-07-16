package net.preibisch.mvrecon.process.resave;

public class MultiResolutionParameters
{
	boolean setMipmapManual;
	int[][] resolutions;
	int[][] subdivisions;

	public MultiResolutionParameters( final boolean setMipmapManual, final int[][] resolutions, final int[][] subdivisions )
	{
		this.setMipmapManual = setMipmapManual;
		this.resolutions = resolutions;
		this.subdivisions = subdivisions;
	}
	
	public int[][] getResolutions() { return resolutions; }
	public int[][] getSubdivisions() { return subdivisions; }
	public boolean getMipmapManual() { return setMipmapManual; }

	public void setResolutions( final int[][] resolutions ) { this.resolutions = resolutions; }
	public void setSubdivisions( final int[][] subdivisions ) { this.subdivisions = subdivisions; }
	public void setMipmapManual( final boolean setMipmapManual ) { this.setMipmapManual = setMipmapManual; }
	
}
