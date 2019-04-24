package net.preibisch.mvrecon.process.fusion.transformed.images;

import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public abstract class AbstractTransformedImgRandomAccessible< T extends RealType< T > > extends AbstractTransformedIntervalRandomAccessible
{
	final protected RandomAccessibleInterval< T > img;

	final protected boolean hasMinValue;
	final protected float minValue;

	public AbstractTransformedImgRandomAccessible(
			final RandomAccessibleInterval< T > img, // from ImgLoader
			final boolean hasMinValue,
			final float minValue,
			final FloatType outsideValue,
			final Interval boundingBox )
	{
		super( img, outsideValue, boundingBox );

		this.img = img;
		this.hasMinValue = hasMinValue;
		this.minValue = minValue;
	}
}
