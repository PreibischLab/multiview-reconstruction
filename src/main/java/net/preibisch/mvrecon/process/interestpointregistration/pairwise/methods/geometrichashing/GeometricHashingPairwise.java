/*-
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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.geometrichashing;

import java.util.ArrayList;
import java.util.List;

import net.preibisch.legacy.mpicbg.PointMatchGeneric;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac.RANSAC;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

public class GeometricHashingPairwise< I extends InterestPoint > implements MatcherPairwise< I >
{
	final RANSACParameters rp;
	final GeometricHashingParameters gp;

	public GeometricHashingPairwise(
			final RANSACParameters rp,
			final GeometricHashingParameters gp )
	{ 
		this.rp = rp;
		this.gp = gp;
	}

	@Override
	public PairwiseResult< I > match( final List< I > listAIn, final List< I > listBIn )
	{
		final PairwiseResult< I > result = new PairwiseResult<>( true );
		final GeometricHasher< I > hasher = new GeometricHasher<>();
		
		final ArrayList< I > listA = new ArrayList<>();
		final ArrayList< I > listB = new ArrayList<>();

		for ( final I i : listAIn )
			listA.add( i );

		for ( final I i : listBIn )
			listB.add( i );

		final int minPoints = 3 + gp.getRedundancy() + 1;

		if ( listA.size() < minPoints || listB.size() < minPoints )
		{
			result.setResult( System.currentTimeMillis(), "Not enough detections to match" );
			result.setCandidates( new ArrayList< PointMatchGeneric< I > >() );
			result.setInliers( new ArrayList< PointMatchGeneric< I > >(), Double.NaN );
			return result;
		}

		final ArrayList< PointMatchGeneric< I > > candidates = hasher.extractCorrespondenceCandidates( 
				listA,
				listB,
				gp.getDifferenceThreshold(),
				gp.getRedundancy(),
				gp.getRatioOfDistance() );

		result.setCandidates( candidates );

		// compute ransac and remove inconsistent candidates
		final ArrayList< PointMatchGeneric< I > > inliers = new ArrayList<>();

		final Pair< String, Double > ransacResult = RANSAC.computeRANSAC( candidates, inliers, gp.getModel(), rp.getMaxEpsilon(), rp.getMinInlierRatio(), rp.getMinInlierFactor(), rp.getNumIterations() );

		result.setInliers( inliers, ransacResult.getB() );

		result.setResult( System.currentTimeMillis(), ransacResult.getA() );

		return result;
	}

	/**
	 * We run RANSAC on these points which makes copies, so no need to duplicate points
	 */
	@Override
	public boolean requiresInterestPointDuplication() { return false; }
}
