/*
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package net.preibisch.mvrecon.process.interestpointdetection.methods.lazygauss;

import java.util.function.Consumer;

import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * Simple Gaussian filter Op
 *
 * @author Stephan Saalfeld
 * @author Stephan Preibisch
 * @param <T> type of input and output
 */
public class PlainGaussRA<T extends RealType<T> & NativeType<T>> implements Consumer<RandomAccessibleInterval<T>>
{
	final T type;
	final private double[] sigmas;
	final long[] globalMin;
	final private RandomAccessible<T> source;

	public Interval total;

	public PlainGaussRA(
			final long[] min,
			final RandomAccessible<T> source,
			final T type,
			final double[] sigmas)
	{
		this.source = source;
		this.globalMin = min;
		this.type = type;
		this.sigmas = sigmas;
	}

	// Note: the output RAI typically sits at 0,0...0 because it usually is a CachedCellImage
	// (but the actual interval to process in many blocks sits somewhere else) 
	@Override
	public void accept( final RandomAccessibleInterval<T> output )
	{
		try
		{
			//long[] min= new long[ output.numDimensions() ];
			//for ( int d = 0; d < min.length; ++d )
			//	min[ d ] = globalMin[ d ] + output.min( d );

			Gauss3.gauss(sigmas, source, Views.translate( output, globalMin ), 1 );
		}
		catch (final IncompatibleTypeException e)
		{
			throw new RuntimeException(e);
		}
	}
}
