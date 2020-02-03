/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
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
package net.preibisch.stitcher.algorithm.globalopt;


public class GlobalOptimizationParameters
{
	public static int defaultGlobalOpt = 2;
	public static boolean defaultExpertGrouping = false;

	public static double defaultRelativeError = 2.5;
	public static double defaultAbsoluteError = 3.5;

	public static int defaultSimple = 3;

	public enum GlobalOptType
	{
		SIMPLE,
		ITERATIVE,
		TWO_ROUND
	}

	public GlobalOptType method;
	public double relativeThreshold;
	public double absoluteThreshold;
	public boolean showExpertGrouping;

	public GlobalOptimizationParameters()
	{
		this( defaultRelativeError, defaultAbsoluteError, GlobalOptType.TWO_ROUND, false );
	}

	public GlobalOptimizationParameters(double relativeThreshold, double absoluteThreshold, GlobalOptType method, boolean showExpertGrouping)
	{
		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
		this.method = method;
		this.showExpertGrouping = showExpertGrouping;
	}
}
