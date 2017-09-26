package net.preibisch.mvrecon.fiji.plugin.boundingbox;

import java.awt.Font;
import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;

public class AutomaticReorientationBoundingBoxGUI extends BoundingBoxGUI
{
	public static String reorientationDescription = "Reorientation to minimize bounding box";
	public String[] reorientationChoice = new String[]{
			"Reorient the sample and find smallest Bounding Box",
			"Only find smallest Bounding Box, do NOT reorientate the sample" };

	public static int defaultReorientate = 0;
	public static int defaultDetections = 1;
	public static double defaultPercent = 10;

	int reorientate;
	List< ViewId > viewIdsToApply;

	public AutomaticReorientationBoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	protected boolean allowModifyDimensions() { return true; }

	@Override
	protected boolean setUpDefaultValues( final int[] rangeMin, final int[] rangeMax )
	{
		if ( !findRange( spimData, viewIdsToProcess, rangeMin, rangeMax ) )
			return false;

		this.min = rangeMin.clone();
		this.max = rangeMax.clone();

		if ( defaultMin == null )
			defaultMin = min.clone();

		if ( defaultMax == null )
			defaultMax = max.clone();

		final GenericDialog gd = new GenericDialog( getDescription() );

		gd.addChoice( "Reorientation", reorientationChoice, reorientationChoice[ defaultReorientate ] );

		// ask which detections to use
		gd.addMessage( "" );
		gd.addMessage( "Note: The bounding box is estimated based on detections in the image, choose which ones to use.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public BoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new AutomaticReorientationBoundingBoxGUI( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Automatically reorientate & estimate from (corresponding) interest points";
	}

}
