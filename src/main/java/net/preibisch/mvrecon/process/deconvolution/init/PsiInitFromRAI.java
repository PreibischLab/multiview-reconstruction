package net.preibisch.mvrecon.process.deconvolution.init;

import java.util.List;
import java.util.concurrent.ExecutorService;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.deconvolution.DeconView;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class PsiInitFromRAI implements PsiInit
{
	final RandomAccessibleInterval< FloatType > input;
	PsiInit init2; // for max and avg

	/**
	 * @param psiRAI - what is used as init for the deconvolved image
	 * @param precise - if avg and max[] of input are computed approximately or precise
	 */
	public PsiInitFromRAI( final RandomAccessibleInterval< FloatType > psiRAI, final boolean precise )
	{
		this.input = psiRAI;

		if ( precise )
		{
			final PsiInitAvgPrecise init2 = new PsiInitAvgPrecise();
			init2.setImgToAvg( false );
			this.init2 = init2;
		}
		else
		{
			final PsiInitAvgApprox init2 = new PsiInitAvgApprox();
			init2.setImgToAvg( false );
			this.init2 = init2;
		}
	}
	
	@Override
	public boolean runInitialization(
			final Img< FloatType > psi,
			final List< DeconView > views,
			final ExecutorService service )
	{
		for ( int d = 0; d < psi.numDimensions(); ++d )
			if ( input.dimension( d ) != psi.dimension( d ) )
			{
				IOFunctions.println( "Image dimensions do not match: " + Util.printInterval( input ) + " != " + Util.printInterval( psi ) );
				return false;
			}

		FusionTools.copyImg( Views.zeroMin( input ), Views.zeroMin( psi ) );

		IOFunctions.println( "RandomAccessibleInterval copied onto PSI for init, now approx computing of avg & max." );

		return init2.runInitialization( psi, views, service );
	}

	@Override
	public double getAvg() { return init2.getAvg(); }

	@Override
	public float[] getMax() { return init2.getMax(); }
}
