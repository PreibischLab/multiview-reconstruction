package net.preibisch.mvrecon.process.deconvolution.init;

public class PsiInitAvgPreciseFactory implements PsiInitFactory
{
	@Override
	public PsiInitAvgPrecise createPsiInitialization()
	{
		return new PsiInitAvgPrecise();
	}
}
