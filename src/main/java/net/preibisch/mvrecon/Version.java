package net.preibisch.mvrecon;

import java.io.File;
import java.net.URISyntaxException;

public class Version
{
	final static String notFound = "JAR version could not be read.";

	private Version() {}

	public static String getVersion()
	{ 
		try
		{
			String name = new File( Version.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath() ).getName().trim();
			
			if ( name.equals( "classes" ) || !name.endsWith( ".jar" ) )
			{
				return notFound;
			}
			else
			{
				// e.g. SPIM_Registration-2.0.0.jar
				final int start = name.indexOf( "-" ) + 1;
				final int end = name.length() - 4;
				
				if ( end <= start || start < 0 )
					return notFound;
				else
					return name.substring( start, end );
			}
		}
		catch ( final URISyntaxException e )
		{
			return notFound;
		}
	}
}
