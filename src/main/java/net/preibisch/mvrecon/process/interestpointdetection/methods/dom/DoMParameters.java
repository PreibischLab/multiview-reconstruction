package net.preibisch.mvrecon.process.interestpointdetection.methods.dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointdetection.methods.InterestPointParameters;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;

/**
 * Created by schmied on 01/07/15.
 */
public class DoMParameters extends InterestPointParameters
{
	/**
	 * 0 = no subpixel localization
	 * 1 = quadratic fit
	 */
	public int localization = 1;

	public int radius1 = 2;
	public int radius2 = 3;
	public float threshold = (float) 0.005;
	public boolean findMin = false;
	public boolean findMax = true;
}
