/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.datasetmanager.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.primes.Primes;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;


public class RegularTranformHelpers {
	
	public static class GridPreset
	{
		public boolean[] alternating;
		public boolean[] increasing;
		public String dimensionOrder;
		
		public GridPreset(boolean[] alternating, boolean[] increasing, String dimensionOrder)
		{
			this.alternating = alternating;
			this.increasing = increasing;
			this.dimensionOrder = dimensionOrder;
		}
		
	}

	public static final List<GridPreset> presets = new ArrayList<>();
	static
	{
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {true, true, true} , "x,y,z" ) );
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {false, true, true} , "x,y,z" ) );
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {true, false, true} , "x,y,z" ) );
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {false, false, true} , "x,y,z" ) );
		
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {true, true, true} , "y,x,z" ) );
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {false, true, true} , "y,x,z" ) );
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {true, false, true} , "y,x,z" ) );
		presets.add( new GridPreset( new boolean[] {false, false, false}, new boolean[] {false, false, true} , "y,x,z" ) );

		presets.add( new GridPreset( new boolean[] {true, false, false}, new boolean[] {true, true, true} , "x,y,z" ) );
		presets.add( new GridPreset( new boolean[] {false, true, false}, new boolean[] {true, false, true} , "y,x,z" ) );
		presets.add( new GridPreset( new boolean[] {true, false, false}, new boolean[] {false, true, true} , "x,y,z" ) );
		presets.add( new GridPreset( new boolean[] {false, true, false}, new boolean[] {false, false, true} , "y,x,z" ) );

		presets.add( new GridPreset( new boolean[] {true, false, false}, new boolean[] {true, false, true} , "x,y,z" ) );
		presets.add( new GridPreset( new boolean[] {false, true, false}, new boolean[] {true, false, true} , "y,x,z" ) );
		presets.add( new GridPreset( new boolean[] {true, false, false}, new boolean[] {false, false, true} , "y,x,z" ) );
		presets.add( new GridPreset( new boolean[] {false, true, false}, new boolean[] {false, false, true} , "x,y,z" ) );
	}

	

	public static class RegularTranslationParameters
	{
		public int nDimensions;
		public int[] nSteps;
		public double[] overlaps;
		public int[] dimensionOrder;
		public boolean[] alternating;
		public boolean[] increasing;
		public boolean keepRotation;
	}

	/*
	 * get dimension order array from string containing x,y and z separated by commas
	 * returns null for malformed strings
	 */
	public static int[] getDimensionOrder(String s)
	{
		List<String> splitted = Arrays.asList( s.split( "," ) );

		for (int i = 0; i < splitted.size(); ++i)
			splitted.set( i, splitted.get( i ).replaceAll( "\\s+", "" ).toUpperCase() );

		if ((splitted.size() < 2) || (splitted.size() > 3))
			return null;

		Set<String> splittedSet = new HashSet<>(splitted);
		splittedSet.add( "Z" );

		//System.out.println( splittedSet );
		if (!(splittedSet.size() == 3 ) || !splittedSet.contains( "X" ) || !splittedSet.contains( "Y" ) || !splittedSet.contains( "Z" ))
			return null;

		int res[] = new int[3];
		res[0] = (int)(char)splitted.get( 0 ).charAt( 0 ) - 88;
		res[1] = (int)(char)splitted.get( 1 ).charAt( 0 ) - 88;
		res[2] = splitted.size() == 3 ? (int)(char)splitted.get( 2 ).charAt( 0 ) - 88 : 2;

		return res;
	}
	
	public static List< Translation3D > generateRegularGrid(RegularTranslationParameters params, Dimensions dims)
	{
		
		final ArrayList< Translation3D > transforms = new ArrayList<>();
		int nTiles = prod(params.nSteps);

		// we have 0 tiles, return empty list
		if (nTiles < 1)
			return transforms;


		for (int i = 0; i < nTiles; ++i)
			transforms.add(new Translation3D());

		int modulo = 1;
		for (int i = 0; i < params.nDimensions; ++i)
		{
			int d = params.dimensionOrder[i];
			double moveSize = (1.0 - params.overlaps[d]) * dims.dimension( d );			

			moveD(transforms, d, moveSize, modulo, params.nSteps[d], params.alternating[d], params.increasing[d]);
			modulo *= params.nSteps[d];
			
		}

		if (params.keepRotation)
		{
			final ArrayList< Translation3D > transformsCentered = new ArrayList<>();
			final Translation3D centerTranslation = getCenterTranslation( transforms, dims );
			for (final Translation3D t : transforms)
			{
				transformsCentered.add( t.preConcatenate( centerTranslation ).copy() );
			}
			return transformsCentered;
		}

		return transforms;
	}

	private static Translation3D getCenterTranslation(Collection<Translation3D> translations, Dimensions size)
	{
		final int n = translations.iterator().next().numDimensions();
		double[] centerTranslation = new double[n];
		int count = 0;
		for (final Translation3D t : translations)
		{
			for (int d = 0; d<n; d++)
				centerTranslation[d] += t.getTranslation( d );
			count++;
		}
		for (int d = 0; d<n; d++)
		{
			centerTranslation[d] /= count;
			centerTranslation[d] += size.dimension( d ) / 2;
			centerTranslation[d] *= -1;
		}
		return new Translation3D( centerTranslation );
		
	}
	
	private static void moveD(	List< Translation3D > transforms, 
								int d,
								double moveSize,
								int modulo,
								int steps, 
								boolean alternate, 
								boolean increasing)
	{
		for (int i = 0; i < transforms.size(); ++i)
		{
			int stepNo = i / (modulo * steps) ;
			int inStep = alternate && stepNo % 2 != 0 ? steps - 1 - i / modulo % steps: i / modulo % steps;
			transforms.get( i ).set( (increasing ? 1.0 : -1.0) * inStep * moveSize, d );			
		}
		
	}

	private static int prod(int ... v)
	{
		int res = 1;
		for (int i = 0; i < v.length; ++i)
			res *= v[i];
		return res;
	}

	public static <AS extends AbstractSpimData<?> > void applyToSpimData(
			AS data, 
			List<? extends Group< ? extends BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions,
			RegularTranslationParameters params,
			boolean applyToAllTimePoints)
	{
		if (!applyToAllTimePoints)
			applyToSpimDataSingleTP( data, viewDescriptions, params, viewDescriptions.get( 0 ).iterator().next().getTimePoint() );
		else
		{
			for (TimePoint tp : data.getSequenceDescription().getTimePoints().getTimePointsOrdered())
				applyToSpimDataSingleTP( data, viewDescriptions, params, tp );
		}
	}

	public static Pair<Double, Integer> getRoatationFromMetadata(Angle a)
	{
		if (!isUnitVector( a.getRotationAxis() ))
			return null;

		final Double rotRadians = a.getRotationAngleDegrees() / 180 * Math.PI;
		final Integer axis = getRotationAxisFromUnitVector( a.getRotationAxis() );
		return new ValuePair< Double, Integer >( rotRadians, axis );
	}

	private static boolean isUnitVector(double[] vec)
	{
		if (vec == null)
			return false;
		int num1s = 0;
		int num0s = 0;
		for (int d = 0; d<vec.length; d++)
		{
			if (Math.abs( vec[d] - 1.0 ) < 1E6 )
				num1s += 1;
			if (Math.abs( vec[d] ) < 1E6)
				num0s += 1;
		}
		return (num0s == vec.length - 1) && (num1s == 1);
	}

	private static int getRotationAxisFromUnitVector(double[] axis)
	{
		for (int d = 0; d<axis.length; d++)
			if (Math.abs( axis[d] - 1.0 ) < 1E6 )
				return d;
		return -1;
	}

	private static <AS extends AbstractSpimData<?> > void applyToSpimDataSingleTP(
			AS data, 
			List< ? extends Group< ? extends BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions,
			RegularTranslationParameters params,
			TimePoint tp)
	{
		Dimensions size = data.getSequenceDescription().getViewDescriptions()
				.get( viewDescriptions.get( 0 ).iterator().next() ).getViewSetup().getSize();
		List< Translation3D > generateRegularGrid = RegularTranformHelpers.generateRegularGrid( params, size );

		int i = 0;
		for (Group<? extends BasicViewDescription< ? >> lvd : viewDescriptions)
		{
			for (BasicViewDescription< ? > vd : lvd)
			{
				// only do for present Views
				if (data.getSequenceDescription().getViewDescriptions().get( new ViewId( tp.getId(), vd.getViewSetupId() ) ).isPresent())
				{
					ViewRegistration vr = data.getViewRegistrations().getViewRegistration( tp.getId(), vd.getViewSetupId() );
					ViewTransform calibration = vr.getTransformList().get( vr.getTransformList().size() - 1 );
					vr.getTransformList().clear();
					vr.getTransformList().add( calibration );
					vr.updateModel();

					// get translation and multiply shift with calibration
					AffineTransform3D translation = new AffineTransform3D();
					translation.set( generateRegularGrid.get( i ).copy().getRowPackedCopy());
					translation.set( translation.get( 0, 3 ) * calibration.asAffine3D().get( 0, 0 ), 0, 3 );
					translation.set( translation.get( 1, 3 ) * calibration.asAffine3D().get( 1, 1 ), 1, 3 );
					translation.set( translation.get( 2, 3 ) * calibration.asAffine3D().get( 2, 2 ), 2, 3 );
					vr.preconcatenateTransform( new ViewTransformAffine( "Translation to Regular Grid", translation ));

					if (params.keepRotation)
					{
						AffineTransform3D rotation = new AffineTransform3D();
						Pair< Double, Integer > rotAngleAndAxis = getRoatationFromMetadata( vd.getViewSetup().getAttribute( Angle.class ) );
						if (rotAngleAndAxis != null)
						{
							rotation.rotate( rotAngleAndAxis.getB(), rotAngleAndAxis.getA() );
							vr.preconcatenateTransform( new ViewTransformAffine( "Rotation from Metadata", rotation.copy() ));
						}
					}
					vr.updateModel();

					//System.out.println(translation);
				}
			}

			// break if we do not have any more transforms to apply
			// the remaining views will be left as-is
			if(++i >= generateRegularGrid.size())
				break;
		}

	}

	public static Pair<Integer, Integer> suggestTiles(int numTiles){
		if (numTiles <= 2)
			return new ValuePair< Integer, Integer >( numTiles, 1 );
		// go to next non-prime
		while (Primes.isPrime( numTiles ))
			numTiles++;
		List< Integer > factors = Primes.primeFactors( numTiles );
		while (factors.size() > 2)
		{
			factors.set( 0, factors.get( 0 ) * factors.get( 2 ));
			factors.remove( 2 );
			if (factors.size() == 2)
				break;
			factors.set( 1, factors.get( 1 ) * factors.get( 2 ));
			factors.remove( 2 );
		}
		return new ValuePair< Integer, Integer >( factors.get( 0 ), factors.get( 1 ) );
	}

}
