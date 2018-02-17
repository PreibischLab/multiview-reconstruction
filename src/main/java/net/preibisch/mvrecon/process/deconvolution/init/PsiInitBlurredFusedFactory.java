package net.preibisch.mvrecon.process.deconvolution.init;

public class PsiInitBlurredFusedFactory implements PsiInitFactory
{
	@Override
	public PsiInitBlurredFused createPsiInitialization()
	{
		return new PsiInitBlurredFused();
	}
}
