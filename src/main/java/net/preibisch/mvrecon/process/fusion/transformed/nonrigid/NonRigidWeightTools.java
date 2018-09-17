package net.preibisch.mvrecon.process.fusion.transformed.nonrigid;

import java.util.Collection;

import mpicbg.models.AffineModel3D;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.grid.ModelGrid;
import net.preibisch.mvrecon.process.fusion.transformed.weights.InterpolatingNonRigidRasteredRandomAccessible;
import net.preibisch.mvrecon.process.fusion.transformed.weights.NonRigidRasteredRandomAccessible;

public class NonRigidWeightTools
{
	public static RandomAccessibleInterval< FloatType > transformWeightNonRigidInterpolated(
			final RealRandomAccessible< FloatType > rra,
			final ModelGrid grid,
			final AffineModel3D invertedModelOpener,
			final Interval boundingBox )
	{
		final long[] offset = new long[ rra.numDimensions() ];
		final long[] size = new long[ rra.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] = boundingBox.min( d );
			size[ d ] = boundingBox.dimension( d );
		}

		// the virtual weight construct
		final RandomAccessible< FloatType > virtualBlending =
				new InterpolatingNonRigidRasteredRandomAccessible< FloatType >(
					rra,
					new FloatType(),
					grid,
					invertedModelOpener,
					offset );

		final RandomAccessibleInterval< FloatType > virtualBlendingInterval = Views.interval( virtualBlending, new FinalInterval( size ) );

		return virtualBlendingInterval;
	}

	public static RandomAccessibleInterval< FloatType > transformWeightNonRigid(
			final RealRandomAccessible< FloatType > rra,
			final Collection< ? extends NonrigidIP > ips,
			final double alpha,
			final AffineModel3D invertedModelOpener,
			final Interval boundingBox )
	{
		final long[] offset = new long[ rra.numDimensions() ];
		final long[] size = new long[ rra.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] = boundingBox.min( d );
			size[ d ] = boundingBox.dimension( d );
		}

		// the virtual weight construct
		final RandomAccessible< FloatType > virtualBlending =
				new NonRigidRasteredRandomAccessible< FloatType >(
					rra,
					new FloatType(),
					ips,
					alpha,
					invertedModelOpener,
					offset );

		final RandomAccessibleInterval< FloatType > virtualBlendingInterval = Views.interval( virtualBlending, new FinalInterval( size ) );

		return virtualBlendingInterval;
	}

}
