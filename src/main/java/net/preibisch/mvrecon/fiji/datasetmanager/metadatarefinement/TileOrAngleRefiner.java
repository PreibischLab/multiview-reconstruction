package net.preibisch.mvrecon.fiji.datasetmanager.metadatarefinement;

import java.util.List;

import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileOrAngleInfo;

import loci.formats.IFormatReader;

public interface TileOrAngleRefiner
{
	public void refineTileOrAngleInfo( IFormatReader r, List<FileListDatasetDefinitionUtil.TileOrAngleInfo> infos);
}