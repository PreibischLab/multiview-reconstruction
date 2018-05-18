package net.preibisch.mvrecon.process.fusion.balancing;

import mpicbg.models.AffineModel1D;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.real.FloatType;

public class IntensityAdjuster implements Converter< FloatType, FloatType >
{
	final AffineModel1D intensityTransform;
	final double[] l = new double[ 1 ];

	public IntensityAdjuster( final AffineModel1D intensityTransform )
	{
		this.intensityTransform = intensityTransform.copy();
	}

	@Override
	public void convert( final FloatType input, final FloatType output )
	{
		this.l[ 0 ] = input.get();
		this.intensityTransform.applyInPlace( this.l );
		output.set( (float)l[ 0 ] );
	}
}
