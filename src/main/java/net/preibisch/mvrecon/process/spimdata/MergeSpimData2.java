/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package net.preibisch.mvrecon.process.spimdata;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import bdv.export.ExportMipmapInfo;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.spimdata.tools.ChangeAttributeId;
import bdv.spimdata.tools.ChangeViewSetupId;
import bdv.spimdata.tools.MergePartitionList;
import bdv.spimdata.tools.MergeTools;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

/**
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt; and Stephan Preibisch &lt;stephan.preibisch@gmx.de&gt;
 */
public class MergeSpimData2
{
	public static boolean mergeHdf5Timepoints( final SpimData2 host, final SpimData2 toAdd )
	{
		if ( compatibleForTimePoints( host, toAdd ) )
		{
			final ImgLoader ilA = host.getSequenceDescription().getImgLoader();
			final ImgLoader ilB = toAdd.getSequenceDescription().getImgLoader();

			if ( Hdf5ImageLoader.class.isInstance( ilA ) && Hdf5ImageLoader.class.isInstance( ilB ) )
			{
				final ArrayList< Partition > newPartitions = new ArrayList<>();
				final Map< Integer, ExportMipmapInfo > newMipmapInfos = new HashMap<>();

				final ArrayList< Partition > partitions = MergePartitionList.getPartitions( seq );
				final Map< Integer, ExportMipmapInfo > mipmapInfos = MergePartitionList.getHdf5PerSetupExportMipmapInfos( seq );

			}
			else
			{
				IOFunctions.println( "Number of viewsetups is not equal." );
				return false;
			}
		}

		return false;
	}

	public static boolean compatibleHDF5(
			final SpimData2 spimDataA,
			final SpimData2 spimDataB )
	{
		final ImgLoader ilA = spimDataA.getSequenceDescription().getImgLoader();
		final ImgLoader ilB = spimDataB.getSequenceDescription().getImgLoader();

		if ( Hdf5ImageLoader.class.isInstance( ilA ) && Hdf5ImageLoader.class.isInstance( ilB ) )
			return true;
		else
			return false;
	}

	public static boolean compatibleForTimePoints(
			final SpimData2 spimDataA,
			final SpimData2 spimDataB )
	{
		final List< ViewSetup > vsA = spimDataA.getSequenceDescription().getViewSetupsOrdered();
		final List< ViewSetup > vsB = spimDataB.getSequenceDescription().getViewSetupsOrdered();

		if ( vsA.size() != vsB.size() )
		{
			IOFunctions.println( "Number of viewsetups is not equal." );
			return false;
		}

		for ( int i = 0; i < vsA.size(); ++i )
		{
			final ViewSetup vA = vsA.get( i );
			final ViewSetup vB = vsB.get( i );

			if ( vA.getId() != vB.getId() )
			{
				IOFunctions.println( "Viewsetups ID's do not match: " + vA.getId() + " <> " + vB.getId() );
				return false;
			}

			if ( vA.getAttributes().size() != vB.getAttributes().size() )
			{
				IOFunctions.println( "Number of attributes for viewsetups is not equal: " + vA.getId() + " <> " + vB.getId() );
				return false;
			}

			final Dimensions dimA = vA.getSize();
			final Dimensions dimB = vB.getSize();
			final VoxelDimensions voxA = vA.getVoxelSize();
			final VoxelDimensions voxB = vB.getVoxelSize();

			if ( dimA == null || dimB == null )
			{
				IOFunctions.println( "Dimensions are null: " + vA.getId() + " <> " + vB.getId() );
				return false;
			}

			if ( voxA == null || voxB == null )
			{
				IOFunctions.println( "Voxel Dimensions are null: " + vA.getId() + " <> " + vB.getId() );
				return false;
			}

			final int n = dimA.numDimensions();

			if ( n != dimB.numDimensions() || n != voxA.numDimensions() || n != voxB.numDimensions() )
			{
				IOFunctions.println( "Dimensionality is not constant: " + vA.getId() + " <> " + vB.getId() );
				return false;
			}

			for ( int d = 0; d < n; ++d )
			{
				if ( dimA.dimension( d ) != dimB.dimension( d ) )
				{
					IOFunctions.println( "Dimensions do not match: " + vA.getId() + " <> " + vB.getId() );
					return false;
				}

				if ( voxA.dimension( d ) != voxB.dimension( d ) )
				{
					IOFunctions.println( "Dimensions do not match: " + vA.getId() + " <> " + vB.getId() );
					return false;
				}
			}

			final HashSet< String > attributes = new HashSet<>();
			attributes.addAll( vA.getAttributes().keySet() );
			attributes.addAll( vB.getAttributes().keySet() );

			for ( final String attrib : attributes )
			{
				final Entity eA = vA.getAttributes().get( attrib );
				final Entity eB = vB.getAttributes().get( attrib );

				if ( eA == null || eB == null || eA.getId() != eB.getId() || !eA.getClass().getSimpleName().equals( eB.getClass().getSimpleName() ) )
				{
					IOFunctions.println( "Attributes for viewsetups do not match for '" + attrib + ", " + vA.getId() + " <> " + vB.getId() );
					return false;
				}
			}
		}

		final MissingViews mvA = spimDataA.getSequenceDescription().getMissingViews();
		final MissingViews mvB = spimDataB.getSequenceDescription().getMissingViews();

		if ( mvA != null || mvB != null )
		{
			if ( mvA == null && mvB.getMissingViews().size() > 0 || mvB == null && mvA.getMissingViews().size() > 0 )
			{
				IOFunctions.println( "Missing views do not match." );
				return false;
			}

			if ( mvA.getMissingViews().size() > 0 || mvB.getMissingViews().size() > 0 )
			{
				IOFunctions.println( "Cannot merge when missing views are present (yet)." );
				return false;
			}
		}

		return true;
	}

	/**
	 * Merge multiple HDF5 datasets, where each dataset contains the same
	 * timepoints but different views.
	 *
	 * @param inputFilenames
	 * 	xml file names for input datasets
	 * @param transforms
	 *  transforms to apply to each input dataset
	 * @param outputXmlFilename
	 * 	xml filename into which to store the merged dataset. An HDF5 link master file with the same basename and extension ".h5" will be created that links into the source hdf5s.
	 * @throws SpimDataException
	 */
	public static void mergeHdf5Views(
			final List< String > inputFilenames,
			final List< ViewTransform > transforms,
			final String outputXmlFilename )
					throws SpimDataException
	{
		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();

		final HashMap< String, Set< Integer > > attributeIdsInUse = new HashMap<>();
		final HashSet< Integer > setupIdsInUse = new HashSet<>();
		final ArrayList< Partition > newPartitions = new ArrayList<>();
		final Map< Integer, ExportMipmapInfo > newMipmapInfos = new HashMap<>();
		final ArrayList< SpimDataMinimal > spimDatas = new ArrayList<>();
		for ( int i = 0; i < inputFilenames.size(); ++i )
		{
			final String fn = inputFilenames.get( i );
			final ViewTransform transform = transforms.get( i );

			final SpimDataMinimal spimData = io.load( fn );
			final SequenceDescriptionMinimal seq = spimData.getSequenceDescription();
			final ArrayList< Partition > partitions = MergePartitionList.getPartitions( seq );
			final Map< Integer, ExportMipmapInfo > mipmapInfos = MergePartitionList.getHdf5PerSetupExportMipmapInfos( seq );

			final Map< String, Map< Integer, Integer > > attributesReassigned = ChangeAttributeId.assignNewAttributeIds( spimData, attributeIdsInUse );
			final Map< Integer, Integer > setupsReassigned = ChangeViewSetupId.assignNewViewSetupIds( spimData, setupIdsInUse );

			for ( final Partition partition : partitions )
			{
				final Map< Integer, Integer > seqToPart = partition.getSetupIdSequenceToPartition();
				final Map< Integer, Integer > newSeqToPart = new HashMap<>();
				for ( final Entry< Integer, Integer > entry : seqToPart.entrySet() )
				{
					final int oldSeq = entry.getKey();
					final int newSeq = setupsReassigned.containsKey( oldSeq ) ? setupsReassigned.get( oldSeq ) : oldSeq;
					newSeqToPart.put( newSeq, entry.getValue() );
				}
				newPartitions.add( new Partition( partition.getPath(), partition.getTimepointIdSequenceToPartition(), newSeqToPart ) );
			}

			for ( final Entry< Integer, ExportMipmapInfo > entry : mipmapInfos.entrySet() )
			{
				final int oldSeq = entry.getKey();
				final int newSeq = setupsReassigned.containsKey( oldSeq ) ? setupsReassigned.get( oldSeq ) : oldSeq;
				newMipmapInfos.put( newSeq, entry.getValue() );
			}

			final ViewRegistrations regs = spimData.getViewRegistrations();
			for ( final ViewRegistration reg : regs.getViewRegistrationsOrdered() )
				reg.concatenateTransform( transform );

			spimDatas.add( spimData );
		}

		final File xmlFile = new File( outputXmlFilename );
		final File path = xmlFile.getParentFile();
		final String xmlFilename = xmlFile.getAbsolutePath();
		final String basename = xmlFilename.endsWith( ".xml" ) ? xmlFilename.substring( 0, xmlFilename.length() - 4 ) : xmlFilename;
		final File h5File = new File( basename + ".h5" );
		if ( h5File.exists() )
			h5File.delete();

		final SpimDataMinimal spimData = MergeTools.merge( path, spimDatas );
		final SequenceDescriptionMinimal seq = spimData.getSequenceDescription();
		final Hdf5ImageLoader imgLoader = new Hdf5ImageLoader( h5File, newPartitions, seq, false );
		seq.setImgLoader( imgLoader );

		WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, newMipmapInfos );
		io.save( spimData, xmlFilename );
	}
}
