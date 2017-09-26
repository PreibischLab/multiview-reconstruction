package net.preibisch.mvrecon.process.interestpointregistration.pairwise;

import java.util.Date;
import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;

public class PairwiseResult< I extends InterestPoint >
{
	private List< PointMatchGeneric< I > > candidates, inliers;
	private double error = Double.NaN;
	private long time = 0;
	private String result = "", desc = "";

	boolean printout = false, storeCorrespondences = true;

	public PairwiseResult( final boolean storeCorrespondences )
	{
		this.storeCorrespondences = storeCorrespondences;
		this.printout = true;
	}

	public boolean storeCorrespondences() { return storeCorrespondences; }
	public void setPrintOut( final boolean printOut ) { this.printout = printOut; }
	public void setResult( final long time, final String result )
	{
		this.time = time;
		this.result = result;
		if ( printout && desc.length() > 0 ) IOFunctions.println( getFullDesc() );
	}
	public void setDescriptions( final String desc ) { this.desc = desc; }
	public List< PointMatchGeneric< I > > getCandidates() { return candidates; }
	public List< PointMatchGeneric< I > > getInliers() { return inliers; }
	public String getDescription() { return desc; }
	public void setDescription( final String desc )
	{
		this.desc = desc;
		if ( printout && result.length() > 0 ) IOFunctions.println( getFullDesc() );
	}
	public double getError() { return error; }
	public void setCandidates( final List< PointMatchGeneric< I > > candidates ) { this.candidates = candidates; }
	public void setInliers( final List< PointMatchGeneric< I > > inliers, final double error )
	{
		this.inliers = inliers;
		this.error = error;
	}

	public String getFullDesc() { return "(" + new Date( time ) + "): " + desc + ": " + result; }
}
