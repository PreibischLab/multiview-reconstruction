package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters;

import java.util.Map;
import java.util.Set;

import mpicbg.models.Model;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.Subset;

public class FixMapBackParameters
{
	public static String[] fixViewsChoice = new String[]{
			"Fix first view",
			"Select fixed view",
			"Do not fix views" };

	public static String[] mapBackChoice = new String[]{
			"Do not map back (use this if views are fixed)",
			"Map back to first view using translation model",
			"Map back to first view using rigid model",
			"Map back to user defined view using translation model",
			"Map back to user defined view using rigid model" };

	public Set< ViewId > fixedViews;
	public Model< ? > model;
	public Map< Subset< ViewId >, Pair< ViewId, Dimensions > > mapBackViews;
}
