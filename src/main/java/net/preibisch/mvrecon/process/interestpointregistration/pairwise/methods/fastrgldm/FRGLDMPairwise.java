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
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.fastrgldm;

import java.util.ArrayList;
import java.util.List;

import net.preibisch.legacy.mpicbg.PointMatchGeneric;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac.RANSAC;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

public class FRGLDMPairwise< I extends InterestPoint > implements MatcherPairwise< I >
{
	final RANSACParameters rp;
	final FRGLDMParameters fp;

	public FRGLDMPairwise(
			final RANSACParameters rp,
			final FRGLDMParameters fp )
	{ 
		this.rp = rp;
		this.fp = fp;
	}

	@Override
	public <V> PairwiseResult<I> match(
			final List<I> listAIn,
			final List<I> listBIn,
			final V viewsA,
			final V viewsB,
			final String labelA,
			final String labelB )
	{
		final PairwiseResult< I > result = new PairwiseResult<>( true );
		final FRGLDMMatcher< I > hasher = new FRGLDMMatcher<>();
		
		final ArrayList< I > listA = new ArrayList<>(listAIn);
		final ArrayList< I > listB = new ArrayList<>(listBIn);

		final int minPoints = fp.getNumNeighbors() + fp.getRedundancy() + 1;

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
				fp.getRedundancy(),
				fp.getRatioOfDistance() );

		result.setCandidates( candidates );

		// compute ransac and remove inconsistent candidates
		final ArrayList< PointMatchGeneric< I > > inliers = new ArrayList<>();

		final Pair< String, Double > ransacResult =
				RANSAC.computeRANSAC(
						candidates,
						inliers,
						fp.getModel(),
						rp.getMaxEpsilon(),
						rp.getMinInlierRatio(),
						rp.getMinNumMatches(),
						rp.getNumIterations(),
						rp.multiConsensus() );

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
