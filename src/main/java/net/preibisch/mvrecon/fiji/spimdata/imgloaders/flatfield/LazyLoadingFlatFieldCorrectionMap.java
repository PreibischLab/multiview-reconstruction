package net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public abstract class LazyLoadingFlatFieldCorrectionMap<IL extends ImgLoader> implements FlatfieldCorrectionWrappedImgLoader< IL >
{
	
	protected final Map< File, RandomAccessibleInterval< FloatType > > raiMap;
	protected final Map<ViewId, Pair<File, File>> fileMap;
	
	public LazyLoadingFlatFieldCorrectionMap()
	{
		raiMap = new HashMap<>();
		fileMap = new HashMap<>();
	}
	
	@Override
	public void setBrightImage(ViewId vId, File imgFile)
	{
		if (!fileMap.containsKey( vId ))
			fileMap.put( vId, new ValuePair< File, File >( null, null ) );

		final Pair< File, File > oldPair = fileMap.get( vId );
		fileMap.put( vId, new ValuePair< File, File >( imgFile, oldPair.getB() ) );
	}

	@Override
	public void setDarkImage(ViewId vId, File imgFile)
	{
		if (!fileMap.containsKey( vId ))
			fileMap.put( vId, new ValuePair< File, File >( null, null ) );

		final Pair< File, File > oldPair = fileMap.get( vId );
		fileMap.put( vId, new ValuePair< File, File >( oldPair.getA(), imgFile ) );
	}
	
	protected RandomAccessibleInterval< FloatType > getBrightImg(ViewId vId)
	{
		if (!fileMap.containsKey( vId ))
			return null;

		final File fileToLoad = fileMap.get( vId ).getA();

		if (fileToLoad == null)
			return null;

		loadFileIfNecessary( fileToLoad );
		return raiMap.get( fileToLoad );
	}

	protected RandomAccessibleInterval< FloatType > getDarkImg(ViewId vId)
	{
		if (!fileMap.containsKey( vId ))
			return null;

		final File fileToLoad = fileMap.get( vId ).getB();

		if (fileToLoad == null)
			return null;

		loadFileIfNecessary( fileToLoad );
		return raiMap.get( fileToLoad );
	}
	
	protected void loadFileIfNecessary(File file)
	{
		if (raiMap.containsKey( file ))
			return;
		
		final ImagePlus imp = IJ.openImage( file.getAbsolutePath() );
		final RandomAccessibleInterval< FloatType > img = ImageJFunctions.convertFloat( imp ).copy();
		
		raiMap.put( file, img );
	}
	
	public static void main(String[] args)
	{
		DefaultFlatfieldCorrectionWrappedImgLoader testImgLoader = new DefaultFlatfieldCorrectionWrappedImgLoader( null );
		testImgLoader.setBrightImage( new ViewId(0,0), new File("/Users/David/Desktop/ell2.tif" ));
		RandomAccessibleInterval< FloatType > brightImg = testImgLoader.getBrightImg( new ViewId( 0, 0 ) );
		
		ImageJFunctions.show( brightImg );
		
	}

	public Map< ViewId, Pair< File, File > > getFileMap()
	{
		return fileMap;
	}
	

}
