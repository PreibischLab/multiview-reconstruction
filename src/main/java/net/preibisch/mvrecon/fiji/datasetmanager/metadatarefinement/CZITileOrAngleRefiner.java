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
package net.preibisch.mvrecon.fiji.datasetmanager.metadatarefinement;

import java.util.List;

import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil;
import net.preibisch.mvrecon.fiji.datasetmanager.StackList;
import ome.units.quantity.Length;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileOrAngleInfo;

import loci.formats.IFormatReader;
import loci.formats.meta.MetadataRetrieve;

public class CZITileOrAngleRefiner implements TileOrAngleRefiner
{
	public void refineTileOrAngleInfo( IFormatReader r, List<FileListDatasetDefinitionUtil.TileOrAngleInfo> infos)
	{
		final int nSeries = r.getSeriesCount();
		final int numDigits = Integer.toString( nSeries ).length();
		
		double[] referenceFramePosition = new double[] {0.0, 0.0, 0.0};
		
		for (int i = 0; i < infos.size(); i++)
		{	
			FileListDatasetDefinitionUtil.TileOrAngleInfo infoT = infos.get( i );
			
			Object tmp = r.getMetadataValue("Information|Image|V|View|Offset #" + ( i+1 ));
			if (tmp == null)
				tmp = r.getMetadataValue("Information|Image|V|View|Offset #" + StackList.leadingZeros( Integer.toString( i + 1 ), numDigits ) );
			
			double angleT = (tmp != null) ?  Double.parseDouble( tmp.toString() ) : 0;			
			infoT.angle = angleT;
			
			tmp = r.getMetadataValue( "Information|Image|V|AxisOfRotation #1" );
			if ( tmp != null && tmp.toString().trim().length() >= 5 )
			{
				//IOFunctions.println( "Rotation axis: " + tmp );
				final String[] axes = tmp.toString().split( " " );

				if ( Double.parseDouble( axes[ 0 ] ) == 1.0 )
					infoT.axis = 0;
				else if ( Double.parseDouble( axes[ 1 ] ) == 1.0 )
					infoT.axis = 1;
				else if ( Double.parseDouble( axes[ 2 ] ) == 1.0 )
					infoT.axis = 2;
				else
				{
					infoT.axis = null;
				}
			}

			final MetadataRetrieve mr = (MetadataRetrieve) r.getMetadataStore();

			Length posX = mr.getPlanePositionX( i, 0 );
			Length posY = mr.getPlanePositionY( i, 0 );
			Length posZ = mr.getPlanePositionZ( i, 0 );
			
			Double pszX = (mr.getPixelsPhysicalSizeX( i ) != null) ? mr.getPixelsPhysicalSizeX( i ).value().doubleValue() : 1.0;
			Double pszY = (mr.getPixelsPhysicalSizeY( i ) != null) ? mr.getPixelsPhysicalSizeY( i ).value().doubleValue() : 1.0;
			Double pszZ = (mr.getPixelsPhysicalSizeZ( i ) != null) ? mr.getPixelsPhysicalSizeZ( i ).value().doubleValue() : 1.0;

			// parse locations or default to 0.0
			infoT.locationX = (posX != null) ? posX.value().doubleValue() * pszX : 0.0;
			infoT.locationY = (posY != null) ? posY.value().doubleValue() * pszY : 0.0;
			infoT.locationZ = (posZ != null) ? posZ.value().doubleValue() * pszZ : 0.0;

			// NOTE: position of first image seems off
			// if we have non-null locations that are not in unit "reference frame", we save them as the reference frame
			// else, we add the offset of the previous reference frame ?
			// FIXME: needs further testing to work correctly

//			if ((posX != null) && !posX.unit().getSymbol().equals( "reference frame" ))
//				referenceFramePosition[0] = infoT.locationX;
//			else
//				infoT.locationX = infoT.locationX + referenceFramePosition[0];
//			
//			if ((posY != null) && !posY.unit().getSymbol().equals( "reference frame" ))
//				referenceFramePosition[1] = infoT.locationY;
//			else
//				infoT.locationY = infoT.locationY + referenceFramePosition[1];
//			
//			if ((posZ != null) && !posZ.unit().getSymbol().equals( "reference frame" ))
//				referenceFramePosition[2] = infoT.locationZ;
//			else
//				infoT.locationZ = infoT.locationZ + referenceFramePosition[2];

			// Old version (pre-LS7?) - will override previous results if the corresponding metadata values are present
			tmp = r.getMetadataValue( "Information|Image|V|View|PositionX #" + StackList.leadingZeros( Integer.toString( i+1 ), numDigits ) );
			if ( tmp == null )
				tmp = r.getMetadataValue( "Information|Image|V|View|PositionX #" + ( i+1 ) );
			infoT.locationX = (tmp != null) ?  Double.parseDouble( tmp.toString() )  : infoT.locationX;

			tmp = r.getMetadataValue( "Information|Image|V|View|PositionY #" + StackList.leadingZeros( Integer.toString( i+1 ), numDigits ) );
			if ( tmp == null )
				tmp = r.getMetadataValue( "Information|Image|V|View|PositionY #"  + ( i+1 ) );
			infoT.locationY = (tmp != null) ?  Double.parseDouble( tmp.toString() )  : infoT.locationY;

			tmp = r.getMetadataValue( "Information|Image|V|View|PositionZ #" + StackList.leadingZeros( Integer.toString( i+1 ), numDigits ) );
			if ( tmp == null )
				tmp = r.getMetadataValue( "Information|Image|V|View|PositionZ #" + ( i+1 ) );
			infoT.locationZ = (tmp != null) ?  Double.parseDouble( tmp.toString() )  : infoT.locationZ;

		}
		
	}
}
