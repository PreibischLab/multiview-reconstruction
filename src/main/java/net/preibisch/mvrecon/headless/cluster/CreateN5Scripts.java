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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.TextFileAccess;
import net.preibisch.mvrecon.Version;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

public class CreateN5Scripts
{
	/**
	 * HOWTO call it using qsub
	 * 
	 * Build multiview-reconstruction FAT-JAR
	 * ./build OR mvn -P fatjar clean package -Denforcer.skip=true
	 * 
	 * Create jobs
	 * ./java -cp multiview-reconstruction-0.3.6-SNAPSHOT.jar net.preibisch.mvrecon.headless.cluster.CreateN5Scripts -r /fast/AG_Preibisch/Stephan/n5/dataset.xml
	 * 
	 * Submit jobs
	 * ./submitAll
	 * 
	 * Verify archive
	 * ./java -cp multiview-reconstruction-0.3.6-SNAPSHOT.jar net.preibisch.mvrecon.headless.cluster.VerifyDistributedN5 -i /fast/AG_Preibisch/Stephan/n5/dataset-n5.xml
	 * 
	 * Tar archive (without compression)
	 * cd /fast/AG_Preibisch/Stephan/n5/
	 * tar -cf n5.tar dataset.n5 dataset-n5.xml
	 */

	public static String submitScript = "submitAll.sh";
	public static String jobFileNamePrefix = "./job_";
	public static String jobFileNameEnding = ".sh";
	public static String filePrefix = "#!/bin/sh";

	public static ClusterSubmission sub = new ClusterSubmissionJanelia();

	public static void main( String[] args ) throws SpimDataException, IOException, InterruptedException
	{
		final Arguments arg = new Arguments( args );

		System.out.println( "getResaveXMLPath: '" + arg.getResaveXMLPath() + "'" );
		System.out.println( "getJarFileName: '" + arg.getJarFileName() + "'" );
		System.out.println( "getInputXMLPath: '" + arg.getInputXMLPath() + "'" );
		System.out.println( "execute: '" + arg.execute() + "'" );

		String resaveXML, jarFileName, inputXML;

		resaveXML = arg.getResaveXMLPath();

		if ( arg.getJarFileName() == null )
			jarFileName = Version.getJARFile( true );
		else
			jarFileName = arg.getJarFileName();

		if ( arg.getInputXMLPath() == null )
			inputXML = resaveXML;
		else
			inputXML = arg.getInputXMLPath();

		// resaveXML = "/fast/AG_Preibisch/Stephan/n5/dataset.xml";
		// inputXML = "/Users/spreibi/Documents/Microscopy/Johnny/dataset.xml";

		System.out.println( "jarFileName: '" + jarFileName + "'" );
		System.out.println( "resaveXML: '" + resaveXML + "'" );

		System.out.println( "Loading input XML: " + inputXML);
		final SpimData2 data = new XmlIoSpimData2( "" ).load( inputXML );

		System.out.println( "Creating scripts..." );
		createScripts( data, jarFileName, resaveXML );

		if ( arg.execute() )
			execute( "./" + submitScript );

		System.out.println( "done" );
	}

	public static void createScripts( final SpimData2 data, final String jarFile, final String resaveXML ) throws SpimDataException
	{
		PrintWriter f = TextFileAccess.openFileWrite( new File( submitScript ) );

		final ArrayList< ViewId > list = new ArrayList<>( data.getSequenceDescription().getViewDescriptions().keySet() );
		Collections.sort( list );

		for ( ViewId v : list )
		{
			if ( data.getSequenceDescription().getMissingViews().getMissingViews().contains( v ) )
				continue;

			String jobName = "N5_" + v.getTimePointId() + "_" + v.getViewSetupId();
			String jobFilename = jobFileNamePrefix + v.getTimePointId() + "_" + v.getViewSetupId() + jobFileNameEnding;
			PrintWriter file = TextFileAccess.openFileWrite( new File( jobFilename ) );
			file.println( filePrefix );
			file.println( getCommand( v, jarFile, resaveXML ) );
			file.close();
			new File( jobFilename ).setExecutable( true );

			f.println( sub.getSubmissionCommand( jobName, jobFilename ) );
		}

		String jobName = "N5_XML";
		String jobFilename = jobFileNamePrefix + "makeXML" + jobFileNameEnding;
		PrintWriter file = TextFileAccess.openFileWrite( new File( jobFilename ) );
		file.println( filePrefix );
		file.println( getCommand( null, jarFile, resaveXML ) );
		file.close();
		new File( jobFilename ).setExecutable( true );

		f.println( sub.getSubmissionCommand( jobName, jobFilename ) );

		f.close();
		new File( submitScript ).setExecutable( true );
	}

	public static void execute( final String command ) throws IOException, InterruptedException
	{
		Runtime run = Runtime.getRuntime();
		Process pr = run.exec( command );
		pr.waitFor();
		BufferedReader buf = new BufferedReader( new InputStreamReader( pr.getInputStream() ) );
		String line = "";
		while ( (line=buf.readLine()) != null )
			System.out.println(line);
	}

	public static String getCommand( final ViewId v, final String jarFile, final String resaveXML )
	{
		String command =
			"./java -Xms4000M -Xmx4000M -cp " + jarFile + " " // multiview-reconstruction-0.3.6-SNAPSHOT.jar
			+ "net.preibisch.mvrecon.fiji.plugin.resave.Resave_N5 "
			+ "-i " + resaveXML + " "; // /fast/AG_Preibisch/Stephan/n5/dataset.xml
	
		if ( v == null )
			command += "-nd";
		else
			command += "-nx -v \"(" + v.getTimePointId() + "," + v.getViewSetupId() + ")\"";

		return command;
	}

	private static class Arguments implements Serializable
	{
		private static final long serialVersionUID = -1467734459169624759L;

		@Option(name = "-r", aliases = { "--resaveXMLPath" }, required = true,
				usage = "Path to the SpimData XML that will be resaved")
		private String resaveXMLPath;

		@Option(name = "-i", aliases = { "--inputXMLPath" }, required = false,
				usage = "Path to an input SpimData XML (optional)")
		private String inputXMLPath;

		@Option(name = "-j", aliases = { "--jarFileName" }, required = false,
				usage = "Path to the fat-jar (optional)")
		private String jarFileName;
		
		@Option(name = "-x", aliases = { "--execute" }, required = false,
				usage = "execute all jobs after creating scripts")
		private boolean exec;

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

		public String getResaveXMLPath()
		{
			return resaveXMLPath;
		}

		public String getJarFileName()
		{
			return jarFileName;
		}

		public boolean execute()
		{
			return exec;
		}
	}
}
