package net.preibisch.mvrecon.fiji.spimdata.explorer.bdv;

import bdv.AbstractSpimSource;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import java.util.Collection;
import java.util.function.BiConsumer;

public class BDVUtils
{
	public static void forEachAbstractSpimSource(
			final Collection< ? extends SourceAndConverter< ? > > sources,
			final BiConsumer< ? super SourceAndConverter< ? >, ? super AbstractSpimSource< ? > > action )
	{
		for ( final SourceAndConverter< ? > soc : sources )
		{
			Source< ? > source = soc.getSpimSource();

			if ( source instanceof TransformedSource )
				source = ( ( TransformedSource< ? > ) source ).getWrappedSource();

			if ( source instanceof AbstractSpimSource )
				action.accept( soc, ( AbstractSpimSource< ? > ) source );
		}
	}

	public static void forEachTransformedSource(
			final Collection< ? extends SourceAndConverter< ? > > sources,
			final BiConsumer< ? super SourceAndConverter< ? >, ? super TransformedSource< ? > > action )
	{
		for ( final SourceAndConverter< ? > soc : sources )
		{
			Source< ? > source = soc.getSpimSource();

			if ( source instanceof TransformedSource )
				action.accept( soc, ( TransformedSource< ? > ) source );
		}
	}
}
