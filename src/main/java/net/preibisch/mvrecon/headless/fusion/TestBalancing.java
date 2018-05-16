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
package net.preibisch.mvrecon.headless.fusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import mpicbg.models.AffineModel1D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.transformed.FusedRandomAccessibleInterval;
import net.preibisch.mvrecon.process.fusion.transformed.TransformView;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.fusion.transformed.TransformWeight;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;

public class TestBalancing
{
	public static void main( String[] args ) throws SpimDataException
	{
		final HashMap< Pair< Integer, Integer >, ArrayList< PointMatch > > intensityMatches = new HashMap<>();

		final ArrayList< PointMatch > pm01 = new ArrayList<>();

		pm01.add( new PointMatch( new Point( new double[] { 5 } ), new Point( new double[] { 10 } ) ) );
		pm01.add( new PointMatch( new Point( new double[] { 10 } ), new Point( new double[] { 20 } ) ) );
		pm01.add( new PointMatch( new Point( new double[] { 50 } ), new Point( new double[] { 100 } ) ) );

		final ArrayList< PointMatch > pm02 = new ArrayList<>();

		pm02.add( new PointMatch( new Point( new double[] { 5 } ), new Point( new double[] { 15 } ) ) );
		pm02.add( new PointMatch( new Point( new double[] { 10 } ), new Point( new double[] { 30 } ) ) );
		pm02.add( new PointMatch( new Point( new double[] { 50 } ), new Point( new double[] { 150 } ) ) );

		final ArrayList< PointMatch > pm12 = new ArrayList<>();

		pm12.add( new PointMatch( new Point( new double[] { 1 } ), new Point( new double[] { 1.5f } ) ) );
		pm12.add( new PointMatch( new Point( new double[] { 10 } ), new Point( new double[] { 15 } ) ) );

		intensityMatches.put( new ValuePair< Integer, Integer >( 0, 1 ), pm01 );
		intensityMatches.put( new ValuePair< Integer, Integer >( 0, 2 ), pm02 );
		intensityMatches.put( new ValuePair< Integer, Integer >( 1, 2 ), pm12 );

		final HashMap< Integer, ViewId > viewMap = new HashMap<>();
		viewMap.put( 0, new ViewId( 0, 1 ) );
		viewMap.put( 1, new ViewId( 0, 2 ) );
		viewMap.put( 2, new ViewId( 0, 3 ) );

		//runGlobal( intensityMatches, viewMap );
		//System.exit(  0 );

		new ImageJ();

		// generate 4 views with 1000 corresponding beads, single timepoint
		// 
		//SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );
		SpimData2 spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/BIMSB/Projects/CLARITY/Big Data Sticher/Neubias_preibisch/GridBalance/dataset.xml" );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testBalance( spimData );
	}

	public static void testBalance( final SpimData2 spimData )
	{
		//Interval bb = TestBoundingBox.testBoundingBox( spimData, false );
		
		Interval bb = spimData.getBoundingBoxes().getBoundingBoxes().get( 0 );
		System.out.println( bb );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// downsampling
		double downsampling = 2;
		double downsamplingEstimation = 8;

		//
		// display virtually fused
		//

		final HashMap< Integer, AffineModel1D > intensityMapping = assembleOverlaps( spimData, viewIds, bb, downsamplingEstimation );

		final RandomAccessibleInterval< FloatType > virtualBalanced = fuseDataBalanced( spimData, viewIds, intensityMapping, bb, downsampling );
		final RandomAccessibleInterval< FloatType > virtual = FusionTools.fuseData( spimData, viewIds, bb, downsampling );

		//
		// actually fuse into an image multithreaded
		//
		final long[] size = new long[ bb.numDimensions() ];
		bb.dimensions( size );

		final RandomAccessibleInterval< FloatType > fusedImg = FusionTools.copyImg( virtual, new ImagePlusImgFactory<>(), new FloatType(), null, true );
		final RandomAccessibleInterval< FloatType > fusedBalancedImg = FusionTools.copyImg( virtualBalanced, new ImagePlusImgFactory<>(), new FloatType(), null, true );

		DisplayImage.getImagePlusInstance( fusedImg, false, "Fused", 0, 255 ).show();
		DisplayImage.getImagePlusInstance( fusedBalancedImg, false, "Fused Balanced", 0, 255 ).show();
	}

	public static RandomAccessibleInterval< FloatType > fuseDataBalanced(
			final AbstractSpimData< ? > spimData,
			final List< ? extends ViewId > viewIds,
			final HashMap< Integer, AffineModel1D > intensityMapping,
			Interval bb,
			double downsampling )
	{
		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( bb, 1.0 / downsampling );

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		for ( int i = 0; i < viewIds.size(); ++i )
		{
			final ViewId viewId = viewIds.get( i );

			final BasicImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			AffineTransform3D model = vr.getModel();

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			final RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, model );

			final float[] blending =  Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
			final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

			// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
			FusionTools.adjustBlending( spimData.getSequenceDescription().getViewDescriptions().get( viewId ), blending, border, model );

			final RandomAccessibleInterval< FloatType > transformedView = TransformView.transformView( inputImg, model, bb, 0, 1 );

			final RandomAccessibleInterval< FloatType > intensityTransformedView = new ConvertedRandomAccessibleInterval< FloatType, FloatType >(
					transformedView, new IntensityAdjuster( intensityMapping.get( i ) ), new FloatType() );

			images.add( intensityTransformedView );
			weights.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );
		}

		return new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
	}

	public static HashMap< Integer, AffineModel1D > assembleOverlaps(
			final AbstractSpimData< ? > spimData,
			final List< ? extends ViewId > viewIds,
			Interval bb,
			double downsampling )
	{
		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( bb, 1.0 / downsampling );

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();

		for ( final ViewId viewId : viewIds )
		{
			final BasicImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			AffineTransform3D model = vr.getModel();

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			final RandomAccessibleInterval inputImg = DownsampleTools.openDownsampled( imgloader, viewId, model );

			final float[] blending =  Util.getArrayFromValue( FusionTools.defaultBlendingRange, 3 );
			final float[] border = Util.getArrayFromValue( FusionTools.defaultBlendingBorder, 3 );

			// adjust both for z-scaling (anisotropy), downsampling, and registrations itself
			FusionTools.adjustBlending( spimData.getSequenceDescription().getViewDescriptions().get( viewId ), blending, border, model );

			images.add( TransformView.transformView( inputImg, model, bb, -1, 1 ) );
		}

		final int m = images.size();

		final HashMap< Integer, ViewId > viewMap = new HashMap<>();
		final Cursor< FloatType > cursor = Views.iterable( images.get( 0 ) ).localizingCursor();
		final ArrayList< RandomAccess< FloatType > > accesses = new ArrayList<>();

		for ( int i = 0; i < m; ++i )
		{
			accesses.add( images.get( i ).randomAccess() );
			viewMap.put( i, viewIds.get( i ) );
		}

		final HashMap< Pair< Integer, Integer >, ArrayList< PointMatch > > intensityMatches = new HashMap<>();

		for ( int i = 0; i < m - 1; ++i )
			for ( int j = i + 1; j < m; ++j )
				intensityMatches.put( new ValuePair< Integer, Integer >( i, j ), new ArrayList<>() );

		final ArrayList< Pair< Integer, Float > > values = new ArrayList<>();

		while ( cursor.hasNext() )
		{
			cursor.fwd();

			values.clear();

			for ( int i = 0; i < m; ++i )
			{
				final RandomAccess< FloatType > r = accesses.get( i );
				r.setPosition( cursor );

				final float value = r.get().get();

				if ( value >= 0 )
					values.add( new ValuePair< Integer, Float >( i, value ) );
			}

			// there are corresponding intensities
			if ( values.size() > 1 )
				for ( int i = 0; i < values.size() - 1; ++i )
					for ( int j = i + 1; j < values.size(); ++j )
					{
						final ArrayList< PointMatch > matches = intensityMatches.get( new ValuePair< Integer, Integer >( values.get( i ).getA(), values.get( j ).getA() ) );
						matches.add( new PointMatch( new Point( new double[] { values.get( i ).getB() } ), new Point( new double[] { values.get( j ).getB() } ) ) );
					}
		}

		return runGlobal( intensityMatches, viewMap );
	}

	/**
	 * @param intensityMatches - all pointmatches for the pairs of images
	 * @param viewMap - links images to ViewIds - integers need to be between 0 and viewMap.keySet().size() - 1
	 * @return the transformations for each image indexed by integer
	 */
	public static HashMap< Integer, AffineModel1D > runGlobal( final HashMap< Pair< Integer, Integer >, ArrayList< PointMatch > > intensityMatches, final HashMap< Integer, ViewId > viewMap )
	{
		final int m = viewMap.keySet().size();

		// create a new tileconfiguration organizing the global optimization
		final TileConfiguration tc = new TileConfiguration();
		
		// assemble a list of all tiles and set them fixed if desired
		final HashMap< Integer, Tile< AffineModel1D > > tiles = new HashMap<>();

		for ( int i = 0; i < m; ++i )
			tiles.put( i, new Tile<>( new AffineModel1D() ) );

		for ( int i = 0; i < m - 1; ++i )
			for ( int j = i + 1; j < m; ++j )
			{
				final Tile< AffineModel1D > ti = tiles.get( i );
				final Tile< AffineModel1D > tj = tiles.get( j );

				final ArrayList< PointMatch > correspondences = intensityMatches.get( new ValuePair< Integer, Integer >( i, j ) );

				System.out.println( Group.pvid( viewMap.get( i ) )  + " <> " + Group.pvid( viewMap.get( j ) ) + ": " + correspondences.size() );
	
				if ( correspondences.size() > 0 )
					addPointMatches( correspondences, ti, tj );
			}

		boolean isFixed = false;

		for ( int i = 0; i < m; ++i )
		{
			final Tile< AffineModel1D > tile = tiles.get( i );

			if ( tile.getConnectedTiles().size() > 0 || tc.getFixedTiles().contains( tile ) )
			{
				tc.addTile( tile );
				if ( !isFixed )
				{
					tc.fixTile( tile );
					isFixed = true;
				}
			}
		}

		try 
		{
			int unaligned = tc.preAlign().size();
			if ( unaligned > 0 )
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): pre-aligned all tiles but " + unaligned );
			else
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): prealigned all tiles" );

			tc.optimize( 5, 1000, 200 );

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + 
				tc.getTiles().size() +  " view-tiles:" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Avg Error: " + tc.getError() + "px" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Min Error: " + tc.getMinError() + "px" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Max Error: " + tc.getMaxError() + "px" );
		}
		catch (NotEnoughDataPointsException e)
		{
			IOFunctions.println( "Global optimization failed: " + e );
			e.printStackTrace();
		}
		catch (IllDefinedDataPointsException e)
		{
			IOFunctions.println( "Global optimization failed: " + e );
			e.printStackTrace();
		}
		
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Transformation Models:" );

		final HashMap< Integer, AffineModel1D > result = new HashMap<>();

		for ( int i = 0; i < m; ++i )
		{
			final Tile< AffineModel1D > tile = tiles.get( i );
			result.put( i, tile.getModel().copy() );
			System.out.println( Group.pvid( viewMap.get( i ) ) + ": " + tile.getModel() );
		}

		return result;
	}

	public static void addPointMatches( final List< ? extends PointMatch > correspondences, final Tile< ? > tileA, final Tile< ? > tileB )
	{
		final ArrayList< PointMatch > pm = new ArrayList<>();
		pm.addAll( correspondences );

		if ( correspondences.size() > 0 )
		{
			tileA.addMatches( pm );
			tileB.addMatches( PointMatch.flip( pm ) );
			tileA.addConnectedTile( tileB );
			tileB.addConnectedTile( tileA );
		}
	}

}
