/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.process.export;

import ij.IJ;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.plugin.Image_Fusion;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccess;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformedInputRandomAccessible;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Paints tiles onto a destination {@link ij.ImagePlus} tile by tile.
 * <p>
 * The hope is that the time performance will be faster: time complexity is
 * O(n), where n is the number of total samples across all tiles, as opposed to
 * implicit algorithm in {@link FusedRandomAccess} which is O(n^2) because every
 * tile must be iterated for every fused sample.
 * </p>
 *
 * @author Curtis Rueden
 * @author Michael Pinkert
 */
public class TileByTileFusion implements ImgExport
{

	@Override
	public boolean finish()
	{
		IJ.showProgress( 1.0 );
		IJ.showStatus( "" );
		return false;
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval< T > img, final Interval bb, final double downsampling, final double anisoF, final String title, final Group< ? extends ViewId > fusionGroup )
	{
		if ( !( img instanceof FusedRandomAccessibleInterval ) )
		{
			throw new IllegalStateException( "This algorithm requires Virtual image type" );
		}
		final FusedRandomAccessibleInterval fused = ( FusedRandomAccessibleInterval ) img;
		final List< ? extends RandomAccessible< FloatType > > tiles = fused.getImages();
		final int ndims = fused.numDimensions();
		final long[] dims = new long[ ndims ];
		fused.dimensions( dims );

		// Allocate destination ImagePlus.
		final FloatImagePlus< FloatType > plane = ImagePlusImgs.floats( dims );

		// Paint tiles one by one onto the destination image.
		// TODO: Multi-thread this!
		int t = 0;
		final int tileCount = tiles.size();
		final long[] min = new long[ ndims ], max = new long[ ndims ];
		final double[] sMin = new double[ ndims ], sMax = new double[ ndims ];
		final double[] tMin = new double[ ndims ], tMax = new double[ ndims ];
		final RandomAccess< FloatType > planeAccess = plane.randomAccess();
		for ( final RandomAccessible< FloatType > tile : tiles )
		{
			// Interval of the tile is too broad, covering the whole fused image.
			// So we compute the real bounding box of the affine transformation.

			// 1) Unwrap the tile.
			if ( !( tile instanceof IntervalView ) )
			{
				throw new IllegalStateException( "Tile #" + t + " is not an IntervalView: " + tile.getClass().getName() );
			}
			final IntervalView< FloatType > tileView = ( IntervalView< FloatType > ) tile;
			final RandomAccessible< FloatType > tileSource = tileView.getSource();
			if ( !( tileSource instanceof TransformedInputRandomAccessible ) )
			{
				throw new IllegalStateException( "Tile #" + t + " does not wrap a TransformedInputRandomAccessible: " + tileSource.getClass().getName() );
			}
			final TransformedInputRandomAccessible< FloatType > transformedTile = ( TransformedInputRandomAccessible< FloatType > ) tileSource;

			// 2) Apply tile's transform to the tile's original bounding box corners.
			// This gives us transformed corners, which we can use to rebound the tile.
			final AffineTransform3D transform = transformedTile.getTransform();
			final long[] offset = transformedTile.offset();
			final RandomAccessibleInterval< FloatType > rawTile = transformedTile.getSource();
			rawTile.min( min );
			rawTile.max( max );
			for ( int d = 0; d < ndims; d++ )
			{
				sMin[ d ] = min[ d ];
				sMax[ d ] = max[ d ];
			}
			transform.apply( sMin, tMin );
			transform.apply( sMax, tMax );
			for ( int d = 0; d < ndims; d++ )
			{
				tMin[ d ] -= offset[ d ];
				tMax[ d ] -= offset[ d ];
			}
			for ( int d = 0; d < ndims; d++ )
			{
				min[ d ] = ( long ) Math.ceil( tMin[ d ] );
				max[ d ] = ( long ) Math.floor( tMax[ d ] );
			}

			// 3) Rebound the transformed tile around the transformed bounding box.
			final RandomAccessibleInterval< FloatType > boundedTile = Views.interval( tileSource, min, max );

			IJ.showProgress( t++, tileCount );
			IJ.showStatus( "Fusing tile " + t + "/" + tileCount );

			// Copy bounded tile onto the plane.
			final Cursor< FloatType > cursor = Views.iterable( boundedTile ).localizingCursor();
			while ( cursor.hasNext() )
			{
				cursor.fwd();
				planeAccess.setPosition( cursor );
				planeAccess.get().set( cursor.get() );
			}
		}

		// Display the completed ImagePlus.
		plane.getImagePlus().resetDisplayRange();
		plane.getImagePlus().show();

		return true;
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval< T > img, final Interval bb, final double downsampling, final double anisoF, final String title, final Group< ? extends ViewId > fusionGroup, final double min, final double max )
	{
		throw new IllegalStateException( "Unimplemented" );
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		return true;
	}

	@Override
	public ImgExport newInstance()
	{
		return new TileByTileFusion();
	}

	@Override
	public String getDescription()
	{
		return "Tile-by-tile O(n) proof of concept";
	}

	public static void main( final String... args ) throws Exception
	{
		new ij.ImageJ();

		// NB: Replicate Image_Fusion.run( arg ) method.

		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		Image_Fusion.fuse( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}
}
