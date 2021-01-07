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
package net.preibisch.mvrecon.headless.cluster;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.img.n5.BdvN5Format;
import bdv.img.n5.N5ImageLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.fiji.plugin.resave.N5Parameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class VerifyDistributedN5
{
	public static void main( String[] args ) throws SpimDataException, IOException
	{
		final Arguments arg = new Arguments( args );

		System.out.println( "Loading input XML: " + arg.getInputXMLPath());
		final SpimData2 data = new XmlIoSpimData2( "" ).load( arg.getInputXMLPath() );

		if ( !N5ImageLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
			throw new RuntimeException( "This is not an XML that is based on an N5 ImgLoader." );

		final N5ImageLoader imgloader = (N5ImageLoader)data.getSequenceDescription().getImgLoader();
		final String n5File = imgloader.getN5File().getAbsolutePath();

		final N5FSWriter n5 = new N5FSWriter( n5File );

		final ArrayList< ViewId > views = new ArrayList<>( data.getSequenceDescription().getViewDescriptions().keySet() );
		Collections.sort( views );

		final String finishedAttribute = N5Parameters.finishedAttrib;

		boolean allSaved = true;
		int countNotSaved = 0;

		for ( final ViewId v : views )
		{
			boolean saved = false;

			final String pathName = BdvN5Format.getPathName( v.getViewSetupId(), v.getTimePointId() );

			try
			{
				final Map< String, Class< ? > > attribs = n5.listAttributes( pathName );
	
				if ( attribs.containsKey( finishedAttribute ) )
					saved = n5.getAttribute( BdvN5Format.getPathName( v.getViewSetupId(), v.getTimePointId() ), finishedAttribute, Boolean.class );
			}
			catch ( IOException e )
			{
				saved = false;
			}

			if ( !saved )
			{
				System.out.println( "View " + Group.pvid( v ) + " was not resaved (yet)." );
				++countNotSaved;
				allSaved = false;
			}
		}

		if ( allSaved )
			System.out.println( "All views were saved as N5 correctly." );
		else
			System.out.println( countNotSaved + "/" + views.size() + " views are not saved as N5 (yet)." );
	}

	private static class Arguments implements Serializable
	{
		private static final long serialVersionUID = -1467734459169624759L;

		@Option(name = "-i", aliases = { "--inputXMLPath" }, required = true,
				usage = "Path to an input SpimData XML")
		private String inputXMLPath;

		public Arguments( final String... args ) throws IllegalArgumentException
		{
			final CmdLineParser parser = new CmdLineParser( this );
			try
			{
				parser.parseArgument( args );
			}
			catch ( final CmdLineException e )
			{
				System.err.println( e.getMessage() );
				parser.printUsage( System.err );
				System.exit( 1 );
			}
		}

		public String getInputXMLPath()
		{
			return inputXMLPath;
		}
	}
}
