package net.preibisch.mvrecon.process.boundingbox;

import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;

public interface BoundingBoxEstimation
{
	public BoundingBox estimate( final String title );
}
