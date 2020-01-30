package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.util.HashMap;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalRealInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class DeconvolutionUtils {
	public static long[] maxTransformedKernel( final HashMap< ViewId, PointSpreadFunction > psfs, final ViewRegistrations vr )
	{
		long[] maxDim = null;
		int n = -1;

		for ( final ViewId viewId : psfs.keySet() )
		{
			final PointSpreadFunction psf = psfs.get( viewId );
			final Img< FloatType > img = psf.getPSFCopy();

			if ( maxDim == null )
			{
				n = img.numDimensions();
				maxDim = new long[ n ];
			}

			final ViewRegistration v = vr.getViewRegistration( viewId );
			v.updateModel();
			final FinalRealInterval bounds = v.getModel().estimateBounds( img );

			System.out.println( Group.pvid( viewId ) + ": " + IOFunctions.printRealInterval( bounds ) );

			// +3 should be +1, but just to be safe
			for ( int d = 0; d < maxDim.length; ++d )
				maxDim[ d ] = Math.max( maxDim[ d ], Math.round( Math.abs( bounds.realMax( d ) - bounds.realMin( d ) ) ) + 3 );
		}

		return maxDim;
	}
}
