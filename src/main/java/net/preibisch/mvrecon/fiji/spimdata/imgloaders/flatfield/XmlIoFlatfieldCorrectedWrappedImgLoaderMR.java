package net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield;

import mpicbg.spim.data.generic.sequence.ImgLoaderIo;

/**
 * Dummy class so we can use XmlIoFlatfieldCorrectedWrappedImgLoader for both
 * multiresolution and non-multiresolution ImgLoaders with flatfield correction
 * 
 * @author david
 *
 */

@ImgLoaderIo(format = "spimreconstruction.wrapped.flatfield.multiresolution", type = MultiResolutionFlatfieldCorrectionWrappedImgLoader.class)
public class XmlIoFlatfieldCorrectedWrappedImgLoaderMR extends XmlIoFlatfieldCorrectedWrappedImgLoader
{
}
