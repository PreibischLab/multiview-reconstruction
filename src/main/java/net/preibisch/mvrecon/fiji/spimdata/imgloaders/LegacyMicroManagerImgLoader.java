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
package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.datasetmanager.MicroManager;
import util.ImgLib2Tools;

public class LegacyMicroManagerImgLoader extends AbstractImgLoader
{
	final File mmFile;
	final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription;

	public LegacyMicroManagerImgLoader(
			final File mmFile,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription )
	{
		super();
		this.mmFile = mmFile;
		this.sequenceDescription = sequenceDescription;

	}

	public File getFile() { return mmFile; }

	final public static < T extends RealType< T > & NativeType< T > > void populateImage( final ArrayImg< T, ? > img, final BasicViewDescription< ? > vd, final MultipageTiffReader r )
	{
		final ArrayCursor< T > cursor = img.cursor();
		
		final int t = vd.getTimePoint().getId();
		final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
		final int c = vd.getViewSetup().getAttribute( Channel.class ).getId();
		final int i = vd.getViewSetup().getAttribute( Illumination.class ).getId();

		int countDroppedFrames = 0;
		ArrayList< Integer > slices = null;

		for ( int z = 0; z < r.depth(); ++z )
		{
			final String label = MultipageTiffReader.generateLabel( r.interleavedId( c, a ), z, t, i );
			final Pair< Object, HashMap< String, Object > > result = r.readImage( label );

			if ( result == null )
			{
				++countDroppedFrames;
				if ( slices == null )
					slices = new ArrayList<Integer>();
				slices.add( z );

				// leave the slice empty
				for ( int j = 0; j < img.dimension( 0 ) * img.dimension( 1 ); ++j )
					cursor.next();

				continue;
			}

			final Object o = result.getA();

			if ( o instanceof byte[] )
				for ( final byte b : (byte[])o )
					cursor.next().setReal( UnsignedByteType.getUnsignedByte( b ) );
			else
				for ( final short s : (short[])o )
					cursor.next().setReal( UnsignedShortType.getUnsignedShort( s ) );
		}

		if ( countDroppedFrames > 0 )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): WARNING!!! " + countDroppedFrames + " DROPPED FRAME(s) in timepoint="  + t + " viewsetup=" + vd.getViewSetupId() + " following slices:" );

			for ( final int z : slices )
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): slice=" + z );
		}
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		if ( normalize )
			return ImgLib2Tools.normalizeVirtual( getImage( view ) );
		else
			return ImgLib2Tools.convertVirtual( getImage( view ) );
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		try
		{
			final MultipageTiffReader r = new MultipageTiffReader( mmFile );

			final ArrayImg< UnsignedShortType, ? > img = ArrayImgs.unsignedShorts( r.width(), r.height(), r.depth() );
			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );

			populateImage( img, vd, r );

			updateMetaDataCache( view, r.width(), r.height(), r.depth(), r.calX(), r.calY(), r.calZ() );

			r.close();

			return img;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void loadMetaData( final ViewId view )
	{
		try
		{
			final MultipageTiffReader r = new MultipageTiffReader( mmFile );

			updateMetaDataCache( view, r.width(), r.height(), r.depth(), r.calX(), r.calY(), r.calZ() );

			r.close();
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load metadata for viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
		}
	}

	@Override
	public String toString()
	{
		return new MicroManager().getTitle() + ", ImgFactory=ArrayImgFactory";
	}
}
