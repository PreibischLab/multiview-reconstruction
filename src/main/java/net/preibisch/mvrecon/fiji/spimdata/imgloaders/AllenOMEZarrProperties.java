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
package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.util.Arrays;
import java.util.Map;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMultiScaleMetadata.OmeNgffDataset;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.CoordinateTransformation;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.coordinateTransformations.ScaleCoordinateTransformation;

import bdv.img.n5.N5Properties;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AllenOMEZarrLoader.OMEZARREntry;

public class AllenOMEZarrProperties implements N5Properties
{
	private final AbstractSequenceDescription< ?, ?, ? > sequenceDescription;

	private final Map< ViewId, OMEZARREntry > viewIdToPath;

	public AllenOMEZarrProperties(
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription,
			final Map< ViewId, OMEZARREntry > viewIdToPath )
	{
		this.sequenceDescription = sequenceDescription;
		this.viewIdToPath = viewIdToPath;
	}

	private String getPath( final int setupId, final int timepointId )
	{
		return viewIdToPath.get( new ViewId( timepointId, setupId ) ).getPath();
	}

	@Override
	public String getDatasetPath( final int setupId, final int timepointId, final int level )
	{
		return String.format( getPath( setupId, timepointId )+ "/%d", level );
	}

	@Override
	public DataType getDataType( final N5Reader n5, final int setupId )
	{
		return getDataType( this, n5, setupId );
	}

	@Override
	public double[][] getMipmapResolutions( final N5Reader n5, final int setupId )
	{
		return getMipMapResolutions( this, n5, setupId );
	}

	@Override
	public long[] getDimensions( final N5Reader n5, final int setupId, final int timepointId, final int level )
	{
		final String path = getDatasetPath( setupId, timepointId, level );
		final long[] dimensions = n5.getDatasetAttributes( path ).getDimensions();
		// dataset dimensions is 5D, remove the channel and time dimensions
		return Arrays.copyOf( dimensions, 3 );
	}

	//
	// static methods
	//

	private static int getFirstAvailableTimepointId( final AbstractSequenceDescription< ?, ?, ? > seq, final int setupId )
	{
		for ( final TimePoint tp : seq.getTimePoints().getTimePointsOrdered() )
		{
			if ( seq.getMissingViews() == null || seq.getMissingViews().getMissingViews() == null || !seq.getMissingViews().getMissingViews().contains( new ViewId( tp.getId(), setupId ) ) )
				return tp.getId();
		}

		throw new RuntimeException( "All timepoints for setupId " + setupId + " are declared missing. Stopping." );
	}

	private static DataType getDataType( final AllenOMEZarrProperties n5properties, final N5Reader n5, final int setupId )
	{
		final int timePointId = getFirstAvailableTimepointId( n5properties.sequenceDescription, setupId );
		return n5.getDatasetAttributes( n5properties.getDatasetPath( setupId, timePointId, 0 ) ).getDataType();
	}

	private static double[][] getMipMapResolutions( final AllenOMEZarrProperties n5properties, final N5Reader n5, final int setupId )
	{
		final int timePointId = getFirstAvailableTimepointId( n5properties.sequenceDescription, setupId );

		// multiresolution pyramid

		//org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v04.OmeNgffMetadata
		// for this to work you need to register an adapter in the N5Factory class
		// final GsonBuilder builder = new GsonBuilder().registerTypeAdapter( CoordinateTransformation.class, new CoordinateTransformationAdapter() );
		final OmeNgffMultiScaleMetadata[] multiscales = n5.getAttribute( n5properties.getPath( setupId, timePointId ), "multiscales", OmeNgffMultiScaleMetadata[].class );

		if ( multiscales == null || multiscales.length == 0 )
			throw new RuntimeException( "Could not parse OME-ZARR multiscales object. stopping." );

		if ( multiscales.length != 1 )
			System.out.println( "This dataset has " + multiscales.length + " objects, we expected 1. Picking the first one." );

		//System.out.println( "AllenOMEZarrLoader.getMipmapResolutions() for " + setupId + " using " + n5properties.getPath( setupId, timePointId ) + ": found " + multiscales[ 0 ].datasets.length + " multi-resolution levels." );

		double[][] mipMapResolutions = new double[ multiscales[ 0 ].datasets.length ][ 3 ];
		double[] firstScale = null;

		for ( int i = 0; i < multiscales[ 0 ].datasets.length; ++i )
		{
			final OmeNgffDataset ds = multiscales[ 0 ].datasets[ i ];

			for ( final CoordinateTransformation< ? > c : ds.coordinateTransformations )
			{
				if ( c instanceof ScaleCoordinateTransformation )
				{
					final ScaleCoordinateTransformation s = ( ScaleCoordinateTransformation ) c;

					if ( firstScale == null )
						firstScale = s.getScale().clone();

					for ( int d = 0; d < mipMapResolutions[ i ].length; ++d )
					{
						mipMapResolutions[ i ][ d ] = s.getScale()[ d ] / firstScale[ d ];
						mipMapResolutions[ i ][ d ] = Math.round(mipMapResolutions[ i ][ d ]*10000)/10000d; // round to the 5th digit
					}
					//System.out.println( "AllenOMEZarrLoader.getMipmapResolutions(), level " + i + ": " + Arrays.toString( s.getScale() ) + " >> " + Arrays.toString( mipMapResolutions[ i ] ) );
				}
			}
		}

		return mipMapResolutions;
	}
}
