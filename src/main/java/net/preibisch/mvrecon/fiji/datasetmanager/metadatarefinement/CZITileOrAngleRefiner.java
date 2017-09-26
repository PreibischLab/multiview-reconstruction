package net.preibisch.mvrecon.fiji.datasetmanager.metadatarefinement;

import java.util.List;

import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil;
import net.preibisch.mvrecon.fiji.datasetmanager.StackList;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileOrAngleInfo;

import loci.formats.IFormatReader;

public class CZITileOrAngleRefiner implements TileOrAngleRefiner
{
	public void refineTileOrAngleInfo( IFormatReader r, List<FileListDatasetDefinitionUtil.TileOrAngleInfo> infos)
	{
		final int nSeries = r.getSeriesCount();
		final int numDigits = Integer.toString( nSeries ).length();
		
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
			
			tmp = r.getMetadataValue( "Information|Image|V|View|PositionX #" + StackList.leadingZeros( Integer.toString( i+1 ), numDigits ) );
			if ( tmp == null )
				tmp = r.getMetadataValue( "Information|Image|V|View|PositionX #" + ( i+1 ) );
			infoT.locationX = (tmp != null) ?  Double.parseDouble( tmp.toString() )  : 0.0;

			tmp = r.getMetadataValue( "Information|Image|V|View|PositionY #" + StackList.leadingZeros( Integer.toString( i+1 ), numDigits ) );
			if ( tmp == null )
				tmp = r.getMetadataValue( "Information|Image|V|View|PositionY #"  + ( i+1 ) );
			infoT.locationY = (tmp != null) ?  Double.parseDouble( tmp.toString() )  : 0.0;

			tmp = r.getMetadataValue( "Information|Image|V|View|PositionZ #" + StackList.leadingZeros( Integer.toString( i+1 ), numDigits ) );
			if ( tmp == null )
				tmp = r.getMetadataValue( "Information|Image|V|View|PositionZ #" + ( i+1 ) );
			infoT.locationZ = (tmp != null) ?  Double.parseDouble( tmp.toString() )  : 0.0;
			
			
			
		}
		
	}
}