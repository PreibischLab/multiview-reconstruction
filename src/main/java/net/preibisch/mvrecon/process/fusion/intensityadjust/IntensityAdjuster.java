package net.preibisch.mvrecon.process.fusion.intensityadjust;

import mpicbg.models.AffineModel1D;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.real.FloatType;

public class IntensityAdjuster implements Converter< FloatType, FloatType >
{
	final double m00, m01;

	public IntensityAdjuster( final AffineModel1D intensityTransform )
	{
		final double[] m = new double[ 2 ];

		intensityTransform.getMatrix( m );
		this.m00 = m[ 0 ];
		this.m01 = m[ 1 ];
	}

	@Override
	public void convert( final FloatType input, final FloatType output )
	{
		// cannot use this because the double[] array l cannot be shared
		//this.intensityTransform.applyInPlace( this.l );

		output.set( (float)apply( input.get() ) );
	}

	final public double apply( final double l )
	{
		return l * m00 + m01;
	}
}
