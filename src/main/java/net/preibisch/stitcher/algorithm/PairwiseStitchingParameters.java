package net.preibisch.stitcher.algorithm;

public class PairwiseStitchingParameters
{
	public double minOverlap;
	public int peaksToCheck;
	public boolean doSubpixel;
	public boolean interpolateCrossCorrelation;
	public boolean showExpertGrouping;
	public boolean useWholeImage;
	
	public PairwiseStitchingParameters()
	{
		this(0, 5, true, false, false, false);
	}

	public PairwiseStitchingParameters(double minOverlap, int peaksToCheck, boolean doSubpixel,
		boolean interpolateCrossCorrelation, boolean showExpertGrouping)
	{
		this(minOverlap, peaksToCheck, doSubpixel, interpolateCrossCorrelation, showExpertGrouping, false);
	}

	public PairwiseStitchingParameters(double minOverlap, int peaksToCheck, boolean doSubpixel,
			boolean interpolateCrossCorrelation, boolean showExpertGrouping, boolean useWholeImage )
	{
		this.minOverlap = minOverlap;
		this.peaksToCheck = peaksToCheck;
		this.doSubpixel = doSubpixel;
		this.interpolateCrossCorrelation = interpolateCrossCorrelation;
		this.showExpertGrouping = showExpertGrouping;
		this.useWholeImage = useWholeImage;
	}

}
