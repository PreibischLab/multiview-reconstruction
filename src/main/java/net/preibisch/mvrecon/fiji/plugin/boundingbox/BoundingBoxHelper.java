package net.preibisch.mvrecon.fiji.plugin.boundingbox;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;

public class BoundingBoxHelper {
	public static BoundingBox getBoundingBox( final SpimData2 spimData, final String bbTitle )
	{
		BoundingBox boundingBox = null;

		for ( final BoundingBox bb : spimData.getBoundingBoxes().getBoundingBoxes() )
		{
			System.out.println( "Bounding box: " + bb.getTitle() );

			if ( bb.getTitle().equals( bbTitle ) )
				boundingBox = bb;
		}

		if ( boundingBox == null )
		{
			System.out.println( "Bounding box '" + bbTitle + "' not found." );
			return null;
		}

		return boundingBox;
	}
}
