package net.preibisch.mvrecon.headless.fusion;

import net.imglib2.Sampler;
import net.imglib2.converter.readwrite.SamplerConverter;
import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;

public final class UnsignedByteDoubleSamplerConverter implements SamplerConverter< UnsignedByteType, DoubleType >
{
	final double maxRange;

	public UnsignedByteDoubleSamplerConverter( final double maxRange )
	{
		this.maxRange = maxRange;
	}

	@Override
	public DoubleType convert( final Sampler< ? extends UnsignedByteType > sampler )
	{
		return new DoubleType( new UnsignedByteConvertingDoubleAccess( sampler, maxRange ) );
	}

	private static final class UnsignedByteConvertingDoubleAccess implements DoubleAccess
	{
		private final Sampler< ? extends UnsignedByteType > sampler;
		private final double maxRange;
		
		private UnsignedByteConvertingDoubleAccess( final Sampler< ? extends UnsignedByteType > sampler, final double maxRange )
		{
			this.sampler = sampler;
			this.maxRange = maxRange;
		}

		/**
		 * This is only intended to work with DoubleType! We ignore index!!!
		 */
		@Override
		public double getValue( final int index )
		{
			return ( sampler.get().getRealDouble() / 255 ) * maxRange;
		}

		/**
		 * This is only intended to work with DoubleType! We ignore index!!!
		 */
		@Override
		public void setValue( final int index, final double value )
		{
			sampler.get().set( (int)Math.round( ( value / maxRange ) * 255 ) );
		}
	}
}