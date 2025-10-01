package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import mpicbg.spim.data.SpimDataException;

public class ExampleDirectLoading
{
	public static void main( String[] args ) throws SpimDataException
	{
		int viewSetupId = 1;

		//
		// works without the XML, just loads the N5 directly
		//
		InterestPointsN5 ip = new InterestPointsN5(
				URI.create("/nrs/saalfeld/john/for/keller/danio_1_488/dataset-orig-tifs/3/"),
				"tpId_0_viewSetupId_" + viewSetupId  + "/beads8v2" );

		List<InterestPoint> points = ip.getInterestPointsCopy();

		for ( final InterestPoint p : points )
		{
			System.out.println( p.getId() + " " + Arrays.toString( p.getL() ));
		}
	}
}
