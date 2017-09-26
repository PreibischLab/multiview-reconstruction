package net.preibisch.mvrecon.process.interestpointdetection;

import java.util.ArrayList;
import java.util.Date;

import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointValue;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussian.SpecialPoint;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.SubpixelLocalization;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.SimplePeak;

public class Localization
{
	public static ArrayList< InterestPoint > noLocalization( final ArrayList< SimplePeak > peaks, final boolean findMin, final boolean findMax, final boolean keepIntensity )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): NO subpixel localization" );

		final int n = peaks.get( 0 ).location.length;
		final ArrayList< InterestPoint > peaks2 = new ArrayList< InterestPoint >();
		
		int id = 0;
		
		for ( final SimplePeak peak : peaks )
		{
			if ( ( peak.isMax && findMax ) || ( peak.isMin && findMin ) )
			{
				final double[] pos = new double[ n ];
				
				for ( int d = 0; d < n; ++d )
					pos[ d ] = peak.location[ d ];
				
				if ( keepIntensity )
					peaks2.add( new InterestPointValue( id++, pos, peak.intensity ) );
				else
					peaks2.add( new InterestPoint( id++, pos ) );
			}
		}
		
		return peaks2;
	}

	public static ArrayList< InterestPoint > computeQuadraticLocalization( final ArrayList< SimplePeak > peaks, final Image< FloatType > domImg, final boolean findMin, final boolean findMax, final float threshold, final boolean keepIntensity )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Subpixel localization using quadratic n-dimensional fit");

		final ArrayList< DifferenceOfGaussianPeak<FloatType> > peakList = new ArrayList<DifferenceOfGaussianPeak<FloatType>>();

		for ( final SimplePeak peak : peaks )
			if ( ( peak.isMax && findMax ) || ( peak.isMin && findMin ) )
				peakList.add( new DifferenceOfGaussianPeak<FloatType>( peak.location, new FloatType( peak.intensity ), SpecialPoint.MAX ) );
		

		final SubpixelLocalization<FloatType> spl = new SubpixelLocalization<FloatType>( domImg, peakList );
		spl.setAllowMaximaTolerance( true );
		spl.setMaxNumMoves( 10 );
		spl.setNumThreads( Threads.numThreads() );

		if ( !spl.checkInput() || !spl.process() )
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Warning! Failed to compute subpixel localization " + spl.getErrorMessage() );
		
		final int n = domImg.getNumDimensions();

		final ArrayList< InterestPoint > peaks2 = new ArrayList< InterestPoint >();
		
		int id = 0;
		
		for ( DifferenceOfGaussianPeak<FloatType> detection : peakList )
		{
			if ( Math.abs( detection.getValue().get() ) > threshold )
			{
				final double[] tmp = new double[ n ];
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = detection.getSubPixelPosition( d );

				if ( keepIntensity )
					peaks2.add( new InterestPointValue( id++, tmp, detection.getValue().get() ) );
				else
					peaks2.add( new InterestPoint( id++, tmp ) );
			}
		}

		return peaks2;
	}
	
	public static ArrayList< InterestPoint > computeGaussLocalization( final ArrayList< SimplePeak > peaks, final Image< FloatType > domImg, final double sigma, final boolean findMin, final boolean findMax, final float threshold, final boolean keepIntensity )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Subpixel localization using Gaussian Mask Localization");					

		// TODO: implement gauss fit
		throw new RuntimeException( "Gauss fit not implemented yet" );
	}	
}
