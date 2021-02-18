/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.io.TextFileAccess;

/**
 * A list of interest points for a certain label, can save and load from textfile as specified in the XML
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class InterestPointList
{
	File baseDir, file;
	List< InterestPoint > interestPoints;
	List< CorrespondingInterestPoints > correspondingInterestPoints;
	String parameters;

	boolean modifiedInterestPoints, modifiedCorrespondingInterestPoints;

	/**
	 * Instantiates a new {@link InterestPointList}
	 * 
	 * @param baseDir - the path where the xml is
	 * @param file - relative path to the file to load/save the list from, an extension is added automatically (.ip.txt &amp;&amp; .corr.txt)
	 * for interestpoints and correspondences
	 */
	public InterestPointList( final File baseDir, final File file )
	{
		this.baseDir = baseDir;
		this.file = file;
		this.interestPoints = null;
		this.correspondingInterestPoints = null;
		this.parameters = "";
		this.modifiedInterestPoints = false;
		this.modifiedCorrespondingInterestPoints = false;
	}

	public boolean hasModifiedInterestPoints() { return modifiedInterestPoints; }
	public boolean hasModifiedCorrespondingInterestPoints() { return modifiedCorrespondingInterestPoints; }

	/**
	 * @return - a list of interest points (copied), tries to load from disc if null
	 */
	public synchronized List< InterestPoint > getInterestPointsCopy()
	{
		if ( this.interestPoints == null )
			loadInterestPoints();

		final ArrayList< InterestPoint > list = new ArrayList< InterestPoint >();

		for ( final InterestPoint p : this.interestPoints )
			list.add( new InterestPoint( p.id, p.getL().clone() ) );

		return list;
	}

	/**
	 * @return - the list of corresponding interest points (copied), tries to load from disc if null
	 */
	public synchronized List< CorrespondingInterestPoints > getCorrespondingInterestPointsCopy()
	{
		if ( this.correspondingInterestPoints == null )
			loadCorrespondences();

		final ArrayList< CorrespondingInterestPoints > list = new ArrayList< CorrespondingInterestPoints >();

		for ( final CorrespondingInterestPoints p : this.correspondingInterestPoints )
			list.add( new CorrespondingInterestPoints( p ) );

		return list;
	}

	public File getBaseDir() { return baseDir; }
	public File getFile() { return file; }
	public String getParameters() { return parameters; }
	public void setParameters( final String parameters ) { this.parameters = parameters; }
	public void setInterestPoints( final List< InterestPoint > list )
	{
		this.interestPoints = list;
		this.modifiedInterestPoints = true;
	}
	public void setCorrespondingInterestPoints( final List< CorrespondingInterestPoints > list )
	{
		this.correspondingInterestPoints = list;
		this.modifiedCorrespondingInterestPoints = true;
	}
	public void setFile( final File file )
	{
		this.file = file;
		this.modifiedCorrespondingInterestPoints = true;
		this.modifiedInterestPoints = true;
	}
	public void setBaseDir( final File baseDir )
	{
		this.baseDir = baseDir;
		this.modifiedCorrespondingInterestPoints = true;
		this.modifiedInterestPoints = true;
	}

	public String getInterestPointsExt() { return ".ip.txt"; }
	public String getCorrespondencesExt() { return ".corr.txt"; }

	public boolean saveInterestPoints( final boolean forceWrite )
	{
		if ( !modifiedInterestPoints && !forceWrite )
			return true;

		final List< InterestPoint > list = this.interestPoints;

		if ( list == null )
			return false;

		try
		{
			final File dir = new File( getBaseDir(), getFile().getParent() );
			
			if ( !dir.exists() )
			{
				IOFunctions.println( "Creating directory: " + dir );
				dir.mkdirs();
			}

			final File f = new File( getBaseDir(), getFile().toString() + getInterestPointsExt() );
			final PrintWriter out = TextFileAccess.openFileWriteEx( f );

			// header
			out.println( "id" + "\t" + "x" + "\t" + "y" + "\t" + "z" );

			// id && coordinates in the local image stack for each interestpoint
			for ( final InterestPoint p : list )
				out.println( Integer.toString( p.getId() ).concat( "\t" ).concat( Double.toString( p.getL()[0] ) ).concat( "\t" ).concat( Double.toString( p.getL()[1] ) ).concat( "\t" ).concat( Double.toString( p.getL()[2] ) ) );

			out.close();

			modifiedInterestPoints = false;

			IOFunctions.println( "Saved: " + f );

			return true;
		}
		catch ( final IOException e )
		{
			IOFunctions.println( "InterestPointList.saveInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}

	public boolean saveCorrespondingInterestPoints( final boolean forceWrite )
	{
		if ( !modifiedCorrespondingInterestPoints && !forceWrite )
			return true;

		final List< CorrespondingInterestPoints > list = this.correspondingInterestPoints;

		if ( list == null )
			return false;

		try
		{
			final File dir = new File( getBaseDir(), getFile().getParent() );
			
			if ( !dir.exists() )
			{
				IOFunctions.println( "Creating directory: " + dir );
				dir.mkdirs();
			}

			final File f = new File( getBaseDir(), getFile().toString() + getCorrespondencesExt() );

			final PrintWriter out = TextFileAccess.openFileWriteEx( f );

			// header
			out.println( "id" + "\t" + "corresponding_timepoint_id" + "\t" + "corresponding_viewsetup_id" + "\t" + "corresponding_label" + "\t" + "corresponding_id" );

			// id of the interestpoint from this List && for the corresponding interestpoint viewid(timepointId, viewsetupId), label, and id
			for ( final CorrespondingInterestPoints p : list )
				out.println(
						Integer.toString( p.getDetectionId() ).concat( "\t" ).concat(
						Integer.toString( p.getCorrespondingViewId().getTimePointId() ) ).concat( "\t" ).concat(
						Integer.toString( p.getCorrespondingViewId().getViewSetupId() ) ).concat( "\t" ).concat(
						p.getCorrespodingLabel() ).concat( "\t" ).concat(
						Integer.toString( p.getCorrespondingDetectionId() ) ) );

			out.close();

			modifiedCorrespondingInterestPoints = false;

			IOFunctions.println( "Saved: " + f );

			return true;
		}
		catch ( final IOException e )
		{
			IOFunctions.println( "InterestPointList.saveCorrespondingInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}

	protected boolean loadCorrespondences()
	{
		try
		{
			final ArrayList< CorrespondingInterestPoints > correspondingInterestPoints = new ArrayList<>();

			final BufferedReader in = TextFileAccess.openFileReadEx( new File( getBaseDir(), getFile().toString() + getCorrespondencesExt() ) );

			// the header
			do {} while ( !in.readLine().startsWith( "id" ) );
			
			while ( in.ready() )
			{
				final String p[] = in.readLine().split( "\t" );
				
				final CorrespondingInterestPoints cip = new CorrespondingInterestPoints(
						Integer.parseInt( p[ 0 ].trim() ),
						new ViewId(
							Integer.parseInt( p[ 1 ].trim() ), // timepointId 
							Integer.parseInt( p[ 2 ].trim() ) ), // viewSetupId
						p[ 3 ], // correspondingLabel,
						Integer.parseInt( p[ 4 ].trim() ) ); //correspondingDetectionId
				
				correspondingInterestPoints.add( cip );
			}

			in.close();

			this.correspondingInterestPoints = correspondingInterestPoints;
			modifiedCorrespondingInterestPoints = false;

			return true;
		}
		catch ( final IOException e )
		{
			this.correspondingInterestPoints = new ArrayList<>();

			// it is normal that this file does not exist until a registration was computed
			System.out.println( "InterestPointList.loadCorrespondingInterestPoints(): " + e );
			return false;
		}
	}

	protected boolean loadInterestPoints()
	{
		try
		{
			final ArrayList< InterestPoint > interestPoints = new ArrayList<>();

			final BufferedReader in = TextFileAccess.openFileReadEx( new File( getBaseDir(), getFile().toString() + getInterestPointsExt() ) );

			// the header
			do {} while ( !in.readLine().startsWith( "id" ) );

			while ( in.ready() )
			{
				final String p[] = in.readLine().split( "\t" );

				final InterestPoint point = new InterestPoint(
						Integer.parseInt( p[ 0 ].trim() ),
						new double[]{
							Double.parseDouble( p[ 1 ].trim() ),
							Double.parseDouble( p[ 2 ].trim() ),
							Double.parseDouble( p[ 3 ].trim() ) } );

				interestPoints.add( point );
			}

			in.close();

			this.interestPoints = interestPoints;
			modifiedInterestPoints = false;

			return true;
		} 
		catch ( final IOException e )
		{
			this.interestPoints = new ArrayList<>();
			IOFunctions.println( "InterestPointList.loadInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}
}
