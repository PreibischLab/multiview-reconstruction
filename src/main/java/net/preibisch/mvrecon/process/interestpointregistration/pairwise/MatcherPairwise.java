package net.preibisch.mvrecon.process.interestpointregistration.pairwise;

import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

public interface MatcherPairwise< I extends InterestPoint >
{
	/**
	 * Computes a pairwise matching between two lists of interestpoints.
	 * 
	 * NOTE: If the interestpoints (local or world coordinates) are changed, you MUST duplicate them before using
	 * them using e.g. LinkedInteresPoint {@literal< I >}
	 * 
	 * @param listAIn interest point list A
	 * @param listBIn interest point list B
	 * @return matched pairwise results
	 */
	public PairwiseResult< I > match( final List< I > listAIn, final List< I > listBIn );

	/**
	 * Determines if this pairwise matching requires a duplication of the input InterestPoints as these instances are ran
	 * multithreaded. So if the InterestPoints are modified in any way (e.g. fitting models to it), this method must return true, otherwise
	 * false (e.g. if only interestpoint.getL() is read).
	 *
	 * @return if duplication is necessary
	 */
	public boolean requiresInterestPointDuplication();
}
