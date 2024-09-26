package net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2;

import java.io.File;
import java.util.Objects;

public class FileMapEntry
{
	private final File file;

	private final int series;

	private final int channel;

	// TODO: rename to FileSeriesChannel ?
	public FileMapEntry( final File file, final int series, final int channel )
	{
		this.file = file;
		this.series = series;
		this.channel = channel;
	}

	public File file()
	{
		return file;
	}

	public int series()
	{
		return series;
	}

	public int channel()
	{
		return channel;
	}

	@Override
	public boolean equals( final Object o )
	{
		if ( this == o )
			return true;
		if ( !( o instanceof FileMapEntry ) )
			return false;
		final FileMapEntry that = ( FileMapEntry ) o;
		return series == that.series && channel == that.channel && Objects.equals( file, that.file );
	}

	@Override
	public int hashCode()
	{
		return Objects.hash( file, series, channel );
	}
}
