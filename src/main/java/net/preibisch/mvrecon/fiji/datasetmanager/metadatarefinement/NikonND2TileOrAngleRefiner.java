package net.preibisch.mvrecon.fiji.datasetmanager.metadatarefinement;

import java.util.List;

import loci.formats.IFormatReader;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileOrAngleInfo;

import ome.xml.meta.MetadataRetrieve;

public class NikonND2TileOrAngleRefiner implements TileOrAngleRefiner
{

	@Override
	public void refineTileOrAngleInfo(IFormatReader r, List< TileOrAngleInfo > infos)
	{
		Double rotation = null;
		Object tmp = r.getGlobalMetadata().get( "Rotate" );
		if (tmp != null)
			rotation = (Double) tmp;
		
		if (rotation != null)
		{
			rotation -= 90;
			AffineTransform2D tr = new AffineTransform2D();
			tr.rotate( -1.0 * rotation / 360.0 * 2 * Math.PI );
			
			for (TileOrAngleInfo info: infos)
			{
				
				double[] loc = new double[] {info.locationX == null ? 0 : info.locationX, info.locationY == null ? 0 : info.locationY};
				tr.apply( loc, loc );
				
				info.locationX = loc[0];
				info.locationY = loc[1];
			}
		}
		
	}

}
