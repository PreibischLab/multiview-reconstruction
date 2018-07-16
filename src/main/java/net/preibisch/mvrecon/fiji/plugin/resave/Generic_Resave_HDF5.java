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
package net.preibisch.mvrecon.fiji.plugin.resave;

import java.io.File;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.process.resave.HDF5Parameters;
import net.preibisch.mvrecon.process.resave.HDF5Tools;

public class Generic_Resave_HDF5 implements PlugIn
{
	public static void main( final String[] args )
	{
		new Generic_Resave_HDF5().run( null );
	}

	@Override
	public void run( final String arg )
	{
		final File file = getInputXML();
		if ( file == null )
			return;

		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();
		SpimDataMinimal spimData;
		try
		{
			spimData = io.load( file.getAbsolutePath() );
		}
		catch ( final SpimDataException e )
		{
			throw new RuntimeException( e );
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = ProposeMipmaps.proposeMipmaps( spimData.getSequenceDescription() );

		final int firstviewSetupId = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final HDF5Parameters params = HDF5Tools.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, true );
		if ( params == null )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// write hdf5
		HDF5Tools.writeHDF5( spimData, params, progressWriter );

		// write xml sequence description
		try
		{
			HDF5Tools.writeXML( spimData, io, params, progressWriter );
		}
		catch ( SpimDataException e )
		{
			throw new RuntimeException( e );
		}
	}

	public static File getInputXML()
	{
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileFilter( new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "xml files";
			}

			@Override
			public boolean accept( final File f )
			{
				if ( f.isDirectory() )
					return true;
				if ( f.isFile() )
				{
					final String s = f.getName();
					final int i = s.lastIndexOf('.');
					if (i > 0 &&  i < s.length() - 1) {
						final String ext = s.substring(i+1).toLowerCase();
						return ext.equals( "xml" );
					}
				}
				return false;
			}
		} );

		if ( fileChooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION )
			return fileChooser.getSelectedFile();
		else
			return null;
	}
}
