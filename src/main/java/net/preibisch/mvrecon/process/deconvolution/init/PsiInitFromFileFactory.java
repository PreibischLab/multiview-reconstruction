package net.preibisch.mvrecon.process.deconvolution.init;

import java.io.File;

public class PsiInitFromFileFactory implements PsiInitFactory
{
	final File psiStartFile;
	final boolean precise;

	/**
	 * @param psiStartFile - what is used as init for the deconvolved image (loaded from disc)
	 * @param precise - if avg and max[] of input are computed approximately or precise
	 */
	public PsiInitFromFileFactory( final File psiStartFile, final boolean precise )
	{
		this.psiStartFile = psiStartFile;
		this.precise = precise;
	}

	@Override
	public PsiInitFromFile createPsiInitialization()
	{
		return new PsiInitFromFile( psiStartFile, precise );
	}
}
