package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.pairwise;

import ij.gui.GenericDialog;

import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.TransformationModelGUI;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.centerofmass.CenterOfMassPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.centerofmass.CenterOfMassParameters;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Center of mass GUI
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class CenterOfMassGUI extends PairwiseGUI
{
	final static String[] centerChoice = new String[]{ "Average", "Median" };
	public static int defaultCenterChoice = 0;

	protected int centerType = 0;

	@Override
	public CenterOfMassPairwise< InterestPoint > pairwiseMatchingInstance()
	{
		return new CenterOfMassPairwise< InterestPoint >( new CenterOfMassParameters( centerType ) );
	}

	@Override
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance()
	{
		return new CenterOfMassPairwise< GroupedInterestPoint< ViewId > >( new CenterOfMassParameters( centerType ) );
	}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addChoice( "Type of Center Computation", centerChoice, centerChoice[ defaultCenterChoice ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		this.centerType = defaultCenterChoice = gd.getNextChoiceIndex();

		return true;
	}

	@Override
	public CenterOfMassGUI newInstance() { return new CenterOfMassGUI(); }

	@Override
	public String getDescription() { return "Center of mass (translation invariant)";}

	@Override
	public TransformationModelGUI getMatchingModel() { return new TransformationModelGUI( 0 ); }

	@Override
	public double getMaxError() { return Double.NaN; }

	@Override
	public double globalOptError() { return 5.0; }
}
