/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.io.File;
import java.net.URI;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import util.URITools;

/**
 * A list of interest points for a certain label, can save and load from textfile as specified in the XML
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public abstract class InterestPoints
{
	public static boolean saveAsN5 = true;

	URI baseDir;
	String parameters;

	boolean modifiedInterestPoints, modifiedCorrespondingInterestPoints;

	protected InterestPoints(final URI baseDir)
	{
		this.baseDir = baseDir;
		this.modifiedInterestPoints = false;
		this.modifiedCorrespondingInterestPoints = false;
	}

	public static InterestPoints instantiatefromXML( final URI baseDir, final String fromXMLInfo )
	{
		if ( fromXMLInfo.trim().toLowerCase().startsWith("interestpoints/") )
			return new InterestPointsTextFileList( baseDir, new File( fromXMLInfo ) );
		else if ( fromXMLInfo.trim().startsWith("tpId_") )
			return new InterestPointsN5( baseDir, fromXMLInfo );
		else
			throw new RuntimeException( "unknown interestpoint representation: '" + fromXMLInfo + "' -- this should not happen.");
	}

	public static InterestPoints newInstance( final URI baseDir, final ViewId viewId, final String label )
	{
		final InterestPoints list;

		if ( saveAsN5 )
		{
			final String n5path = new InterestPointsN5( null, null ).createXMLRepresentation( viewId, label );
			list = new InterestPointsN5( baseDir, n5path );
		}
		else
		{
			if ( !URITools.isFile( baseDir ) )
				throw new RuntimeException( "InterestPointsTextFileList only works for local file systems." );
	
			final String fileName = new InterestPointsTextFileList( null, null ).createXMLRepresentation( viewId, label );
			list = new InterestPointsTextFileList( baseDir, new File( fileName ) );
		}

		return list;
	}

	public boolean hasModifiedInterestPoints() { return modifiedInterestPoints; }
	public boolean hasModifiedCorrespondingInterestPoints() { return modifiedCorrespondingInterestPoints; }

	public URI getBaseDir() { return baseDir; }
	public void setBaseDir( final URI baseDir )
	{
		this.baseDir = baseDir;
		this.modifiedCorrespondingInterestPoints = true;
		this.modifiedInterestPoints = true;
	}

	public abstract boolean deleteInterestPoints();
	public abstract boolean deleteCorrespondingInterestPoints();

	/** 
	 * @return the parameters used for segmenting these points
	 */
	public String getParameters() { return parameters; }
	public void setParameters( final String parameters ) { this.parameters = parameters; }

	/**
	 * @return a string to be stored in the XML, also used to instantiate this object (e.g. file path)
	 */
	public abstract String getXMLRepresentation();

	/**
	 * @param viewId the viewId
	 * @param label the label
	 * 
	 * @return a string that is stored in the XML and that is used to load/save interestpoints and corresponding interest points
	 */
	public abstract String createXMLRepresentation( final ViewId viewId, final String label );

	/**
	 * @return - a list of interest points (copied), tries to load from disc if null
	 */
	public abstract List< InterestPoint > getInterestPointsCopy();

	/**
	 * @return - the list of corresponding interest points (copied), tries to load from disc if null
	 */
	public abstract List< CorrespondingInterestPoints > getCorrespondingInterestPointsCopy();

	public void setInterestPoints( final List< InterestPoint > list )
	{
		this.modifiedInterestPoints = true;
		setInterestPointsLocal( list );
	}
	public void setCorrespondingInterestPoints( final List< CorrespondingInterestPoints > list )
	{
		this.modifiedCorrespondingInterestPoints = true;
		setCorrespondingInterestPointsLocal( list );
	}

	protected abstract void setInterestPointsLocal( final List< InterestPoint > list );
	protected abstract void setCorrespondingInterestPointsLocal( final List< CorrespondingInterestPoints > list );

	public abstract boolean saveInterestPoints( final boolean forceWrite );
	public abstract boolean saveCorrespondingInterestPoints( final boolean forceWrite );

	protected abstract boolean loadCorrespondences();
	protected abstract boolean loadInterestPoints();
}
