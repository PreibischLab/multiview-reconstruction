package net.preibisch.mvrecon.process.deconvolution.util;

import java.util.ArrayList;
import java.util.HashMap;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.psf.PSFCombination;
import net.preibisch.mvrecon.process.psf.PSFExtraction;

public class PSFPreparation
{
	public static < V extends ViewId > HashMap< Group< V >, ArrayImg< FloatType, ? > > loadGroupTransformPSFs(
			final PointSpreadFunctions pointSpreadFunctions,
			final ProcessInputImages< V > fusion )
	{
		final HashMap< ViewId, PointSpreadFunction > rawPSFs = pointSpreadFunctions.getPointSpreadFunctions();
		final HashMap< Group< V >, ArrayImg< FloatType, ? > > psfs = new HashMap<>();

		for ( final Group< V > virtualView : fusion.getGroups() )
		{
			final ArrayList< Img< FloatType > > viewPsfs = new ArrayList<>();
	
			for ( final V view : virtualView )
			{
				// load PSF
				final ArrayImg< FloatType, ? > psf = rawPSFs.get( view ).getPSFCopyArrayImg();

				// remember the normalized, transformed version (including downsampling!)
				viewPsfs.add( PSFExtraction.getTransformedNormalizedPSF( psf, fusion.getDownsampledModels().get( view ) ) );

				//DisplayImage.getImagePlusInstance( viewPsfs.get( viewPsfs.size() - 1 ), false, "psf " + Group.pvid( view ), 0, 1 ).show();
			}

			// compute the PSF for a group by averaging over the minimal size of all inputs
			// the sizes can be different if the transformations are not tranlations but affine.
			// they should, however, not differ significantly but only combine views that have
			// basically the same transformation (e.g. angle 0 vs 180, or before after correction of chromatic abberations)
			psfs.put( virtualView, (ArrayImg< FloatType, ? >)PSFCombination.computeAverageImage( viewPsfs, new ArrayImgFactory< FloatType >(), false ) );

			//DisplayImage.getImagePlusInstance( psfs.get( virtualView ), false, "psf " + virtualView, 0, 1 ).show();
		}

		return psfs;
	}
}
