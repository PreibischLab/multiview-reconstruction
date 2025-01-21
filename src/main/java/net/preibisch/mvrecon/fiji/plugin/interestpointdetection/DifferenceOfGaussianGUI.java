/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.plugin.interestpointdetection;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.ImagePlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive.InteractiveDoGParams;
import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive.InteractiveDoG;
import net.preibisch.mvrecon.fiji.plugin.util.GenericDialogAppender;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.cuda.CUDADevice;
import net.preibisch.mvrecon.process.cuda.CUDASeparableConvolution;
import net.preibisch.mvrecon.process.cuda.CUDATools;
import net.preibisch.mvrecon.process.cuda.NativeLibraryTools;
import net.preibisch.mvrecon.process.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoG;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoGParameters;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class DifferenceOfGaussianGUI extends DifferenceOfGUI implements GenericDialogAppender
{
	public static double defaultUseGPUMem = 75;

	public static double defaultSigma = 1.8;
	public static double defaultThreshold = 0.008;
	public static boolean defaultFindMin = false;
	public static boolean defaultFindMax = true;

	public static String[] computationOnChoice = new String[]{
		"CPU (Java)",
		"GPU approximate (Nvidia CUDA via JNA)",
		"GPU accurate (Nvidia CUDA via JNA)" };
	public static int defaultComputationChoiceIndex = 0;

	double sigma;
	double threshold;
	boolean findMin;
	boolean findMax;

	double percentGPUMem = defaultUseGPUMem;

	/**
	 * CUDA device
	 */
	CUDADevice deviceCUDA = null;
	CUDASeparableConvolution cuda = null;
	boolean accurateCUDA = false;

	public DifferenceOfGaussianGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Difference-of-Gaussian"; }

	@Override
	public DifferenceOfGaussianGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new DifferenceOfGaussianGUI( spimData, viewIdsToProcess );
	}

	@Override
	public HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint t )
	{
		final DoGParameters dog = new DoGParameters();

		dog.imgloader = spimData.getSequenceDescription().getImgLoader();
		dog.toProcess = new ArrayList< ViewDescription >();

		dog.localization = this.localization;
		dog.downsampleZ = this.downsampleZ;
		dog.imageSigmaX = this.imageSigmaX;
		dog.imageSigmaY = this.imageSigmaY;
		dog.imageSigmaZ = this.imageSigmaZ;

		dog.minIntensity = this.minIntensity;
		dog.maxIntensity = this.maxIntensity;

		dog.sigma = this.sigma;
		dog.threshold = this.threshold;
		dog.findMin = this.findMin;
		dog.findMax = this.findMax;

		dog.cuda = this.cuda;
		dog.deviceCUDA = this.deviceCUDA;
		dog.accurateCUDA = this.accurateCUDA;
		dog.percentGPUMem = this.percentGPUMem;

		dog.limitDetections = this.limitDetections;
		dog.maxDetections = this.maxDetections;
		dog.maxDetectionsTypeIndex = this.maxDetectionsTypeIndex;

		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();

		for ( final ViewDescription vd : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, t ) )
		{
			// make sure not everything crashes if one file is missing
			try
			{
				if ( !vd.isPresent() )
					continue;

				dog.toProcess.clear();
				dog.toProcess.add( vd );

				// downsampleXY == 0 : a bit less then z-resolution
				// downsampleXY == -1 : a bit more then z-resolution
				if ( downsampleXYIndex < 1 )
					dog.downsampleXY = DownsampleTools.downsampleFactor( downsampleXYIndex, downsampleZ, vd.getViewSetup().getVoxelSize() );
				else
					dog.downsampleXY = downsampleXYIndex;

				DoG.addInterestPoints( interestPoints, dog );
			}
			catch ( Exception  e )
			{
				IOFunctions.println( "An error occured (DOG): " + e ); 
				IOFunctions.println( "Failed to segment viewId: " + Group.pvid( vd ) + ". Continuing with next one." );
				e.printStackTrace();
			}
		}

		return interestPoints;
	}

	@Override
	protected boolean setDefaultValues( final int brightness )
	{
		this.sigma = defaultSigma;
		this.findMin = false;
		this.findMax = true;

		if ( brightness == 0 )
			this.threshold = 0.001;
		else if ( brightness == 1 )
			this.threshold = 0.008;
		else if ( brightness == 2 )
			this.threshold = 0.03;
		else if ( brightness == 3 )
			this.threshold = 0.1;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues()
	{
		final GenericDialog gd = new GenericDialog( "Advanced values" );

		gd.addNumericField( "Sigma", defaultSigma, 5 );
		gd.addNumericField( "Threshold", defaultThreshold, 5 );
		gd.addCheckbox( "Find_minima", defaultFindMin );
		gd.addCheckbox( "Find_maxima", defaultFindMax );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		this.sigma = defaultSigma = gd.getNextNumber();
		this.threshold = defaultThreshold = gd.getNextNumber();
		this.findMin = defaultFindMin = gd.getNextBoolean();
		this.findMax = defaultFindMax = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues()
	{
		final ImagePlus imp;

		if ( !groupIllums && !groupTiles )
			imp = getImagePlusForInteractive( "Interactive Difference-of-Gaussian" );
		else
			imp = getGroupedImagePlusForInteractive( "Interactive Difference-of-Gaussian" );

		if ( imp == null )
			return false;

		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.show();
		imp.setSlice( imp.getStackSize() / 2 );
		imp.setRoi( 0, 0, imp.getWidth()/3, imp.getHeight()/3 );

		InteractiveDoGParams params = new InteractiveDoGParams();
		params.sigma = (float)defaultSigma;
		params.threshold = (float)defaultThreshold;
		params.findMaxima = defaultFindMax;
		params.findMinima = defaultFindMin;

		final double min, max;

		if ( Double.isNaN( minIntensity ) || Double.isNaN( maxIntensity ) )
		{
			min = imp.getDisplayRangeMin();
			max = imp.getDisplayRangeMax();

			IOFunctions.println( "(" + new Date(System.currentTimeMillis() ) + "): Using approximate min [" + min + "]/max[" + max + "] intensity values ... to have a more accurate preview your can manually set min/max intensity." );
		}
		else
		{
			min = minIntensity;
			max = maxIntensity;
		}

		final InteractiveDoG idog = new InteractiveDoG( imp, params, min, max );
		do
		{
			try
			{
				Thread.sleep( 100 );
			} catch (InterruptedException e) {}
		}
		while (!idog.isFinished());

		imp.close();

		if (idog.wasCanceled())
			return false;

		this.sigma = defaultSigma = params.sigma; //idog.getInitialSigma();
		this.threshold = defaultThreshold = params.threshold; //idog.getThreshold();
		this.findMax = defaultFindMax = params.findMaxima;//idog.getLookForMaxima();
		this.findMin = defaultFindMin = params.findMinima;//idog.getLookForMinima();

		return true;
	}

	@Override
	public String getParameters()
	{
		return "DOG s=" + sigma + " t=" + threshold + " min=" + findMin + " max=" + findMax +
				" imageSigmaX=" + imageSigmaX + " imageSigmaY=" + imageSigmaY + " imageSigmaZ=" + imageSigmaZ + " downsampleXYIndex=" + downsampleXYIndex +
				" downsampleZ=" + downsampleZ + " minIntensity=" + minIntensity + " maxIntensity=" + maxIntensity;
	}

	@Override
	protected void addAddtionalParameters( final GenericDialog gd )
	{
		gd.addChoice( "Compute_on", computationOnChoice, computationOnChoice[ defaultComputationChoiceIndex ] );
		
	}

	@Override
	protected boolean queryAdditionalParameters( final GenericDialog gd )
	{
		final int computationTypeIndex = defaultComputationChoiceIndex = gd.getNextChoiceIndex();

		if ( computationTypeIndex == 1 )
			accurateCUDA = false;
		else
			accurateCUDA = true;

		if ( computationTypeIndex >= 1 )
		{
			final ArrayList< String > potentialNames = new ArrayList< String >();
			potentialNames.add( "separable" );
			
			cuda = NativeLibraryTools.loadNativeLibrary( potentialNames, CUDASeparableConvolution.class );

			if ( cuda == null )
			{
				IOFunctions.println( "Cannot load CUDA JNA library." );
				deviceCUDA = null;
				return false;
			}

			// multiple CUDA devices sometimes crashes, no idea why yet ...
			final ArrayList< CUDADevice > selectedDevices = CUDATools.queryCUDADetails( cuda, false, this );

			if ( selectedDevices == null || selectedDevices.size() == 0 )
				return false;
			else
				deviceCUDA = selectedDevices.get( 0 );
		}
		else
		{
			deviceCUDA = null;
		}

		return true;
	}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addMessage( "" );
		gd.addSlider( "Percent_of_GPU_Memory_to_use", 1, 100, defaultUseGPUMem );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		this.percentGPUMem = defaultUseGPUMem = gd.getNextNumber();
		return true;
	}
}
