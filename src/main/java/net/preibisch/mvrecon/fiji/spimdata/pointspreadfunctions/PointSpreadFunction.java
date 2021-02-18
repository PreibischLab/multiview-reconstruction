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
package net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions;

import java.io.File;

import ij.ImagePlus;
import ij.io.FileSaver;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class PointSpreadFunction
{
	private final static String subDir = "psf";

	private final File xmlBasePath;
	private final String file;
	private Img< FloatType > img;
	private boolean modified;

	public PointSpreadFunction( final File xmlBasePath, final String file, final Img< FloatType > img )
	{
		this.xmlBasePath = xmlBasePath;
		this.file = file;

		if ( img != null )
			this.img = img.copy(); // avoid changes to the PSF if an actual image is provided

		this.modified = true; // not initialized from disc, needs to be saved
	}

	public PointSpreadFunction( final SpimData2 spimData, final ViewId viewId, final Img< FloatType > img  )
	{
		this( spimData.getBasePath(), PointSpreadFunction.createPSFFileName( viewId ), img );
	}

	public PointSpreadFunction( final File xmlBasePath, final String file )
	{
		this( xmlBasePath, file, null );
		this.modified = false;
	}

	public void setPSF( final Img< FloatType > img )
	{
		this.modified = true;
		this.img = img;
	}

	public String getFile() { return file; }
	public boolean isModified() { return modified; }
	public boolean isLoaded() { return img != null; }

	public Img< FloatType > getPSFCopy()
	{
		if ( img == null )
			img = IOFunctions.openAs32Bit( new File( new File( xmlBasePath, subDir ), file ), new ArrayImgFactory<>() );

		return img.copy();
	}

	// this is required for CUDA stuff
	@SuppressWarnings("unchecked")
	public ArrayImg< FloatType, ? > getPSFCopyArrayImg()
	{
		final ArrayImg< FloatType, ? > arrayImg;

		if ( img == null )
		{
			img = arrayImg = IOFunctions.openAs32BitArrayImg( new File( new File( xmlBasePath, subDir ), file ) );
		}
		else if ( ArrayImg.class.isInstance( img ) )
		{
			arrayImg = (ArrayImg< FloatType, ? >)img;
		}
		else
		{
			final long[] size = new long[ img.numDimensions() ];
			img.dimensions( size );

			arrayImg = new ArrayImgFactory< FloatType >().create( size, new FloatType() );

			FusionTools.copyImg( img, arrayImg, null );
		}

		return arrayImg;
	}

	public boolean save()
	{
		if ( img == null )
			return false;

		final File dir = new File( xmlBasePath, subDir );

		if ( !dir.exists() )
			if ( !dir.mkdir() )
				return false;

		final ImagePlus imp = DisplayImage.getImagePlusInstance( img, false, file, 0, 1 );
		final boolean success = new FileSaver( imp ).saveAsTiffStack( new File( dir, file ).toString() );
		imp.close();

		if ( success )
			modified = false;

		return success;
	}

	public static String createPSFFileName( final ViewId viewId )
	{
		return "psf_t" + viewId.getTimePointId() + "_v" + viewId.getViewSetupId() + ".tif";
	}
}
