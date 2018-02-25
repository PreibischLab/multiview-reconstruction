package net.preibisch.mvrecon.process.deconvolution.init;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;

public class PsiInitFromRAIFactory implements PsiInitFactory
{
	final RandomAccessibleInterval< FloatType > psiRAI;
	final boolean precise;

	/**
	 * @param psiRAI - what is used as init for the deconvolved image
	 * @param precise - if avg and max[] of input are computed approximately or precise
	 */
	public PsiInitFromRAIFactory( final RandomAccessibleInterval< FloatType > psiRAI, final boolean precise )
	{
		this.psiRAI = psiRAI;
		this.precise = precise;
	}

	@Override
	public PsiInitFromRAI createPsiInitialization()
	{
		return new PsiInitFromRAI( psiRAI, precise );
	}
}
