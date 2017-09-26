package net.preibisch.mvrecon.process.export;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import fiji.util.gui.GenericDialogPlus;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.io.TiffEncoder;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class Save3dTIFF implements ImgExport
{
	public static boolean defaultUseXMLPath = true;
	public static String defaultPath = null;
	
	String path;
	boolean compress;

	public Save3dTIFF( final String path ) { this( path, false ); }
	public Save3dTIFF( final String path, final boolean compress )
	{ 
		this.path = path;
		this.compress = compress;
	}
	
	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final String title )
	{
		exportImage( img, null, 1.0, title, null );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final String title,
			final Group< ? extends ViewId > fusionGroup )
	{
		return exportImage( img, bb, 1.0, title, fusionGroup, Double.NaN, Double.NaN );
	}

	public <T extends RealType<T> & NativeType<T>> boolean exportImage(
			final RandomAccessibleInterval<T> img,
			final Interval bb,
			final double downsampling,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final double min,
			final double max )
	{
		// do nothing in case the image is null
		if ( img == null )
			return false;
		
		// determine min and max
		final double[] minmax = DisplayImage.getFusionMinMax( img, min, max );

		final ImagePlus imp = DisplayImage.getImagePlusInstance( img, true, title, minmax[ 0 ], minmax[ 1 ] );

		DisplayImage.setCalibration( imp, bb, downsampling );

		imp.updateAndDraw();

		final String fileName;
		
		if ( !title.endsWith( ".tif" ) )
			fileName = new File( path, title + ".tif" ).getAbsolutePath();
		else
			fileName = new File( path, title ).getAbsolutePath();
		
		if ( compress )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving file " + fileName + ".zip" );
			boolean success = new FileSaver( imp ).saveAsZip( fileName );

			if ( success )
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saved file " + fileName + ".zip" );
			else
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED saving file " + fileName + ".zip" );

			return success;
		}
		else
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving file " + fileName );
			boolean success = saveTiffStack( imp, fileName ); //new FileSaver( imp ).saveAsTiffStack( fileName );

			if ( success )
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saved file " + fileName  );
			else
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED saving file " + fileName );

			return success;
		}
	}

	/*
	 * Reimplementation from ImageJ FileSaver class. Necessary since it traverses the entire virtual stack once to collect some
	 * slice labels, which takes forever in this case.
	 */
	public static boolean saveTiffStack( final ImagePlus imp, final String path )
	{
		FileInfo fi = imp.getFileInfo();
		boolean virtualStack = imp.getStack().isVirtual();
		if (virtualStack)
			fi.virtualStack = (VirtualStack)imp.getStack();
		fi.info = imp.getInfoProperty();
		fi.description = new FileSaver( imp ).getDescriptionString();
		DataOutputStream out = null;
		try {
			TiffEncoder file = new TiffEncoder(fi);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		} catch (IOException e) {
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": ERROR: Cannot save file '"+ path + "':" + e );
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return true;
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Save fused images as 3D TIFF" );

		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = fusion.getSpimData().getBasePath().getAbsolutePath();
			
			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );
			
			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );
		}

		PluginHelper.addSaveAsDirectoryField( gd, "Output_file_directory", defaultPath, 80 );
		gd.addCheckbox( "Lossless compression of TIFF files (ZIP)", Resave_TIFF.defaultCompress );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		this.path = defaultPath = gd.getNextString().trim();
		this.compress = Resave_TIFF.defaultCompress = gd.getNextBoolean();

		return true;
	}

	@Override
	public ImgExport newInstance() { return new Save3dTIFF( path ); }

	@Override
	public String getDescription() { return "Save as (compressed) TIFF stacks"; }

	@Override
	public boolean finish()
	{
		// nothing to do
		return false;
	}
}
