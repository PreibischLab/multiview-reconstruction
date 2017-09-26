package net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions;

import java.util.HashMap;

import mpicbg.spim.data.sequence.ViewId;

public class PointSpreadFunctions
{
	private HashMap< ViewId, PointSpreadFunction > psfs;

	public PointSpreadFunctions()
	{
		this.psfs = new HashMap<>();
	}

	public PointSpreadFunctions( final HashMap< ViewId, PointSpreadFunction > psfs )
	{
		this();
		this.psfs.putAll( psfs );
	}

	public HashMap< ViewId, PointSpreadFunction > getPointSpreadFunctions() { return psfs; }
	public void addPSF( final ViewId viewId, final PointSpreadFunction img ) { this.psfs.put( viewId, img ); }
}
