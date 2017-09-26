package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import ij.ImagePlus;
import ij.io.Opener;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

public class LegacyDHMImgLoader extends AbstractImgLoader
{
	final File directory;
	final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sd;
	final List< String > timepoints;
	final List< String > zPlanes;
	final String stackDir;
	final String amplitudeDir;
	final String phaseDir;
	final String extension;
	final int ampChannelId;
	final int phaseChannelId;

	public LegacyDHMImgLoader(
			final File directory,
			final String stackDir,
			final String amplitudeDir,
			final String phaseDir,
			final List< String > timepoints,
			final List< String > zPlanes,
			final String extension,
			final int ampChannelId,
			final int phaseChannelId,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sd )
	{
		this.directory = directory;
		this.stackDir = stackDir;
		this.amplitudeDir = amplitudeDir;
		this.phaseDir = phaseDir;
		this.timepoints = timepoints;
		this.zPlanes = zPlanes;
		this.extension = extension;
		this.ampChannelId = ampChannelId;
		this.phaseChannelId = phaseChannelId;
		this.sd = sd;
	}

	public String getStackDir() { return stackDir; }
	public String getAmplitudeDir() { return amplitudeDir; }
	public String getPhaseDir() { return phaseDir; }
	public List< String > getZPlanes() { return zPlanes; }
	public List< String > getTimepoints() { return timepoints; }
	public int getAmpChannelId() { return ampChannelId; }
	public int getPhaseChannelId() { return phaseChannelId; }
	public String getExt() { return extension; }

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( view );
		final Dimensions d = vd.getViewSetup().getSize();
		final VoxelDimensions dv = vd.getViewSetup().getVoxelSize();

		final ArrayImg< FloatType, ? > img = ArrayImgs.floats( d.dimension( 0 ), d.dimension( 1 ), d.dimension(  2 ) );

		final String ampOrPhaseDir;

		if ( vd.getViewSetup().getAttribute( Channel.class ).getId() == ampChannelId )
			ampOrPhaseDir = amplitudeDir;
		else if ( vd.getViewSetup().getAttribute( Channel.class ).getId() ==  phaseChannelId )
			ampOrPhaseDir = phaseDir;
		else
			throw new RuntimeException( "viewSetupId=" + view.getViewSetupId() + " is not Amplitude nor phase." );

		populateImage( img, directory, stackDir, ampOrPhaseDir, zPlanes, timepoints.get( view.getTimePointId() ), extension );

		if ( normalize )
			normalize( img );

		updateMetaDataCache( view, (int)d.dimension( 0 ), (int)d.dimension( 1 ), (int)d.dimension( 2 ), dv.dimension( 0 ), dv.dimension( 1 ), dv.dimension( 2 ) );

		return img;
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( view );
		final Dimensions d = vd.getViewSetup().getSize();
		final VoxelDimensions dv = vd.getViewSetup().getVoxelSize();

		final ArrayImg< UnsignedShortType, ? > img = ArrayImgs.unsignedShorts( d.dimension( 0 ), d.dimension( 1 ), d.dimension( 2 ) );

		final String ampOrPhaseDir;

		if ( vd.getViewSetup().getAttribute( Channel.class ).getId() == ampChannelId )
			ampOrPhaseDir = amplitudeDir;
		else if ( vd.getViewSetup().getAttribute( Channel.class ).getId() ==  phaseChannelId )
			ampOrPhaseDir = phaseDir;
		else
			throw new RuntimeException( "viewSetupId=" + view.getViewSetupId() + " is not Amplitude nor phase." );

		populateImage( img, directory, stackDir, ampOrPhaseDir, zPlanes, timepoints.get( view.getTimePointId() ), extension );

		updateMetaDataCache( view, (int)d.dimension( 0 ), (int)d.dimension( 1 ), (int)d.dimension( 2 ), dv.dimension( 0 ), dv.dimension( 1 ), dv.dimension( 2 ) );

		return img;
	}

	@Override
	protected void loadMetaData( ViewId view )
	{
		final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( view );
		final Dimensions d = vd.getViewSetup().getSize();
		final VoxelDimensions dv = vd.getViewSetup().getVoxelSize();

		updateMetaDataCache( view, (int)d.dimension( 0 ), (int)d.dimension( 1 ), (int)d.dimension( 2 ), dv.dimension( 0 ), dv.dimension( 1 ), dv.dimension( 2 ) );
	}

	final public static < T extends RealType< T > & NativeType< T > > void populateImage(
			final ArrayImg< T, ? > img,
			final File directory,
			final String stackDir,
			final String ampOrPhaseDirectory,
			final List< String > zPlanes,
			final String timepoint,
			final String extension )
	{
		final Opener io = new Opener();
		final ArrayCursor< T > cursor = img.cursor();

		int countDroppedFrames = 0;
		ArrayList< Integer > slices = null;

		for ( int z = 0; z < zPlanes.size(); ++z )
		{
			final File imgF = new File( new File( new File( new File( directory.getAbsolutePath(), stackDir ), ampOrPhaseDirectory ), zPlanes.get( z ) ), timepoint + extension );
			final ImagePlus imp = io.openImage( imgF.getAbsolutePath() );

			if ( imp == null )
			{
				++countDroppedFrames;
				if ( slices == null )
					slices = new ArrayList< Integer >();
				slices.add( z );

				// leave the slice empty
				for ( int j = 0; j < img.dimension( 0 ) * img.dimension( 1 ); ++j )
					cursor.next();

				continue;
			}

			final Object o = imp.getProcessor().getPixels();

			if ( o instanceof byte[] )
				for ( final byte b : (byte[])o )
					cursor.next().setReal( UnsignedByteType.getUnsignedByte( b ) );
			else if ( o instanceof short[] )
				for ( final short s : (short[])o )
					cursor.next().setReal( UnsignedShortType.getUnsignedShort( s ) );
			else if ( o instanceof float[] )
				for ( final float s : (float[])o )
					cursor.next().setReal( s );
		}

		if ( countDroppedFrames > 0 )
		{
			IOFunctions.println(
					"(" + new Date( System.currentTimeMillis() ) + "): WARNING!!! " + countDroppedFrames +
					" DROPPED FRAME(s) in timepoint="  + timepoint + " channel=" + ampOrPhaseDirectory + " following slices:" );

			for ( final int z : slices )
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): sliceindex=" + z + ", slice=" + zPlanes.get( z ));
		}
	}
}
