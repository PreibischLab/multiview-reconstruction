package net.preibisch.mvrecon.process.deconvolution.init;

public class PsiInitAvgApproxFactory implements PsiInitFactory
{
	@Override
	public PsiInitAvgApprox createPsiInitialization()
	{
		return new PsiInitAvgApprox();
	}
}
