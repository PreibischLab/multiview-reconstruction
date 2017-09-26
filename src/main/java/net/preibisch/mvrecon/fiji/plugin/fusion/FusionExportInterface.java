package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.util.Collection;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;

public interface FusionExportInterface
{
	SpimData getSpimData();

	/**
	 * 0 == "32-bit floating point",
	 * 1 == "16-bit unsigned integer"
	 *
	 * @return the pixel type
	 */
	int getPixelType();

	/**
	 * 0 == "Each timepoint &amp; channel",
	 * 1 == "Each timepoint, channel &amp; illumination",
	 * 2 == "All views together",
	 * 3 == "Each view"
	 * 
	 * @return - how the views that are processed are split up
	 */
	int getSplittingType();

	Interval getDownsampledBoundingBox();
	double getDownsampling();
	Collection< ? extends ViewId > getViews();
}
