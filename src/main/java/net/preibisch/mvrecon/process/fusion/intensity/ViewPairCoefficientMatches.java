package net.preibisch.mvrecon.process.fusion.intensity;

import java.io.Serializable;
import java.util.List;
import mpicbg.spim.data.sequence.ViewId;

public class ViewPairCoefficientMatches implements Serializable {

	private final ViewId view1;
	private final ViewId view2;
	private final List<IntensityMatcher.CoefficientMatch> coefficientMatches;

	public ViewPairCoefficientMatches(
			final ViewId view1,
			final ViewId view2,
			final List<IntensityMatcher.CoefficientMatch> coefficientMatches
	) {
		this.view1 = view1;
		this.view2 = view2;
		this.coefficientMatches = coefficientMatches;
	}

	public ViewId view1() {
		return view1;
	}

	public ViewId view2() {
		return view2;
	}

	public List<IntensityMatcher.CoefficientMatch> coefficientMatches() {
		return coefficientMatches;
	}
}
