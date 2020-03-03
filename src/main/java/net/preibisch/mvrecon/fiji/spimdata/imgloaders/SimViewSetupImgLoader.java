package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;
import java.util.Date;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.plugin.Raw;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.datasetmanager.SimViewMetaData;
import net.preibisch.mvrecon.fiji.datasetmanager.SimViewMetaData.Pattern;

public class SimViewSetupImgLoader implements SetupImgLoader< UnsignedShortType >
{
	final int setupId;
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
	final File expDir;
	final String filePattern;
	final Pattern patternParser;
	final int type;
	final boolean littleEndian;

	public SimViewSetupImgLoader(
			final int setupId,
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription,
			final File expDir,
			final String filePattern,
			final Pattern patternParser,
			final int type,
			final boolean littleEndian )
	{
		this.setupId = setupId;
		this.sequenceDescription = sequenceDescription;
		this.expDir = expDir;
		this.filePattern = filePattern;
		this.type = type;
		this.littleEndian = littleEndian;
		this.patternParser = patternParser;
	}
	
	@Override
	public RandomAccessibleInterval<FloatType> getFloatImage( final int timepointId, final boolean normalize, ImgLoaderHint... hints )
	{
		if ( normalize )
			return AbstractImgLoader.normalizeVirtual( getImage( timepointId, hints ) );
		else
			return AbstractImgLoader.convertVirtual( getImage( timepointId, hints ) );
	}

	@Override
	public RandomAccessibleInterval<UnsignedShortType> getImage( final int timepointId, ImgLoaderHint... hints )
	{
		final ViewId view = new ViewId( timepointId, setupId );
		final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
		final BasicViewSetup vs = vd.getViewSetup();

		final TimePoint t = vd.getTimePoint();
		final Angle a = LegacyLightSheetZ1ImgLoader.getAngle( vd );
		final Channel c = LegacyLightSheetZ1ImgLoader.getChannel( vd );

		String[] code = a.getName().split( "-" );
		final int angle = Integer.parseInt( code[ 0 ] );
		final int cam = Integer.parseInt( code[ 1 ] );

		final File tpFolder = new File( expDir, SimViewMetaData.getTimepointString( t.getId() ) );
		final File angleFolder = new File( tpFolder, SimViewMetaData.getAngleString( angle ) );
				
		final File rawFile = new File( angleFolder, 
				SimViewMetaData.getFileNamesFor(
						this.filePattern,
						patternParser.replaceTimepoints, patternParser.replaceChannels, patternParser.replaceCams, patternParser.replaceAngles,
						t.getId(), c.getId(), cam, angle,
						patternParser.numDigitsTimepoints, patternParser.numDigitsChannels, patternParser.numDigitsCams, patternParser.numDigitsAngles)[0] );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading file " + rawFile.getAbsolutePath() );

		final FileInfo fi = new FileInfo();
		if ( type == 0 )
			fi.fileType = FileInfo.GRAY8;
		else if ( type == 1 )
			fi.fileType = FileInfo.GRAY16_SIGNED;
		else
			fi.fileType = FileInfo.GRAY16_UNSIGNED;
			
		fi.width = (int)vs.getSize().dimension( 0 );
		fi.height = (int)vs.getSize().dimension( 1 );
		fi.nImages = (int)vs.getSize().dimension( 2 );
		fi.intelByteOrder = true;

		System.out.println( fi );

		//final VirtualStack virtual = new FileInfoVirtualStack(fi);
		final ImagePlus imp = Raw.open( rawFile.getAbsolutePath(), fi );

		if ( imp == null )
			throw new RuntimeException( "Could not load '" + rawFile.getAbsolutePath() + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

		if ( type == 0 )
		{
			final RandomAccessibleInterval< UnsignedByteType > img = ImageJFunctions.wrap( imp );

			if ( img == null )
				throw new RuntimeException( "Could not load '" + rawFile.getAbsolutePath() + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			final Converter<UnsignedByteType, UnsignedShortType> conv = new RealUnsignedShortConverter<UnsignedByteType>( 0, 255 );
			return new ConvertedRandomAccessibleInterval< UnsignedByteType, UnsignedShortType >( img, conv, new UnsignedShortType() );
		}
		else
		{
			final RandomAccessibleInterval< UnsignedShortType > img = ImageJFunctions.wrap( imp );

			if ( img == null )
				throw new RuntimeException( "Could not load '" + rawFile.getAbsolutePath() + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			return img;
		}
	}

	@Override
	public UnsignedShortType getImageType() { return new UnsignedShortType(); }

	@Override
	public Dimensions getImageSize( final int timepointId )
	{
		final ViewId view = new ViewId( timepointId, setupId );
		final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
		final BasicViewSetup vs = vd.getViewSetup();
		
		return vs.getSize();
	}

	@Override
	public VoxelDimensions getVoxelSize( final int timepointId )
	{
		final ViewId view = new ViewId( timepointId, setupId );
		final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
		final BasicViewSetup vs = vd.getViewSetup();
		
		return vs.getVoxelSize();
	}
}
