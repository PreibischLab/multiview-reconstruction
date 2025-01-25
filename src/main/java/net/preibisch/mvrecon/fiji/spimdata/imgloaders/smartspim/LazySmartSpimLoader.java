package net.preibisch.mvrecon.fiji.spimdata.imgloaders.smartspim;

import java.util.function.Consumer;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.datasetmanager.SmartSPIM;
import net.preibisch.mvrecon.fiji.datasetmanager.SmartSPIM.SmartSPIMMetaData;
import util.Lazy;
import util.URITools;

public class LazySmartSpimLoader implements Consumer<RandomAccessibleInterval<UnsignedShortType>>
{
	final SmartSPIMMetaData metadata;
	final int channel, xTile, yTile;

	public LazySmartSpimLoader(
			final SmartSPIMMetaData metadata,
			final int channel,
			final int xTile,
			final int yTile )
	{
		this.metadata = metadata;
		this.channel = channel;
		this.xTile = xTile;
		this.yTile = yTile;
	}

	@Override
	public void accept( final RandomAccessibleInterval< UnsignedShortType > output )
	{
		//System.out.println( "loading z=" + output.min( 2 ));

		final ImagePlus imp = metadata.loadImage(
				metadata.channels.get( channel ),
				metadata.xTileLocations.get( xTile ),
				metadata.yTileLocations.get( yTile ),
				metadata.sortedFileNames.get( (int)output.min( 2 )) );

		final short[] pixels = (short[])imp.getProcessor().getPixels();
		final Img<UnsignedShortType> img = ArrayImgs.unsignedShorts( pixels, metadata.dimensions[ 0 ], metadata.dimensions[ 1 ] );

		final Cursor<UnsignedShortType> out = Views.flatIterable( output ).cursor();
		final Cursor<UnsignedShortType> in = Views.flatIterable( img ).cursor();

		while ( in.hasNext() )
			out.next().set( in.next() );
	}

	public static final RandomAccessibleInterval< UnsignedShortType > init(
			final SmartSPIMMetaData meta,
			final int channel,
			final int xTile,
			final int yTile )
	{
		final LazySmartSpimLoader lazyLoader =
				new LazySmartSpimLoader( meta, channel, xTile, yTile );

		final RandomAccessibleInterval< UnsignedShortType > stack =
						Lazy.process(
								new FinalInterval( new FinalDimensions( meta.dimensions ) ),
								new int[] { (int)meta.dimensions[ 0 ], (int)meta.dimensions[ 1 ], 1 },
								new UnsignedShortType(),
								AccessFlags.setOf(),
								lazyLoader );

		return stack;
	}

	public static void main( String[] args )
	{
		SmartSPIMMetaData metadata =
				SmartSPIM.parseMetaDataFile( URITools.toURI( "/Volumes/johnsonlab/LM/20241031_11_59_44_RJ_mouse_2_vDisco_hindleg_right_Destripe_DONE/metadata.json") );

		if ( SmartSPIM.populateImageSize( metadata, false ) )
		{
			new ImageJ();

			ImageJFunctions.show( init( metadata, 0, 0, 0 ) );
		}
	}
}
