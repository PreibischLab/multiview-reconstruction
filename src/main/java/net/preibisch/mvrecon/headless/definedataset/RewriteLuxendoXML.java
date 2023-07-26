package net.preibisch.mvrecon.headless.definedataset;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class RewriteLuxendoXML implements Callable<Void> 
{
	@Option(names = {"-i", "--input"}, required = true, description = "input XML written by Luxendo MuviSPIM, e.g. -i /home/dataset.xml")
	private String input = null;

	@Option(names = {"-o", "--output"}, required = false, description = "output XML for the same Luxendo MuviSPIM containing BigStitcher attributes, e.g. -i /home/dataset_bigstitcher.xml")
	private String output = null;

	// e.g. stack_0-x00-y00_channel_1_obj_right_cam_long
	// patterns: stack_0-x{xx}-y{yy}_channel_{c}_obj_right_cam_long
	final static String searchX = "stack_0-x";
	final static String searchY = "-y";
	final static String searchCh = "_channel_";
	final static String searchObj = "_obj_";

	@Override
	public Void call() throws Exception
	{
		// angle and illumination are constant (for now)
		final Angle angle = new Angle( 0 );
		final Illumination illum = new Illumination( 0 );

		try
		{
			System.out.println( "Loading '" + input + "' ... ");

			final XmlIoSpimData io = new XmlIoSpimData();
			final SpimData spimData = io.load( input );
	
			final SpimData2 spimDataOut = SpimData2.convert( spimData );
			final SequenceDescription s = spimDataOut.getSequenceDescription();
	
			final HashMap< Pair<Integer, Integer>, Integer > tileIds = new HashMap<>();
			final AtomicInteger countTileId = new AtomicInteger(0);

			for ( final ViewDescription vd : s.getViewDescriptions().values() )
			{
				final String name = vd.getViewSetup().getName();

				System.out.print( "Parsing ViewSetup: " + name + " ... " );

				final int[] p = parseName(name); //x,y,ch
				tileIds.computeIfAbsent( new ValuePair<>(p[0], p[1]), k -> countTileId.getAndIncrement() );

				System.out.println( "x=" + p[0] + ", y=" + p[1] + ", ch=" + p[2] );
			}

			for ( final ViewDescription vd : s.getViewDescriptions().values() )
			{
				final String name = vd.getViewSetup().getName();

				final int[] p = parseName(name); //x,y,ch
				final int tileId = tileIds.get( new ValuePair<>(p[0], p[1]) );
				final int channelId = p[2];

				System.out.println( "Adding Attributes to ViewSetup: " + name +": x=" + p[0] + ", y=" + p[1] + ", tileId=" + tileId + ", ch=" + p[2] + ", channelId=" + channelId );

				// set missing attributes
				vd.getViewSetup().setAttribute( angle );
				vd.getViewSetup().setAttribute( illum );
				vd.getViewSetup().setAttribute( new Channel( channelId ) );
				vd.getViewSetup().setAttribute( new Tile(tileId, "x=" + p[0] + ", y=" + p[1] ) );
			}

			System.out.println( "Saving '" + output + "' ... ");

			XmlIoSpimData2.initN5Writing = false;
			final XmlIoSpimData2 io2 = new XmlIoSpimData2( "" );
			io2.save(spimDataOut, output);

			System.out.println( "done.");
		}
		catch ( Exception e )
		{
			System.err.println( "Error rewriting '" + input + "': " + e );
			e.printStackTrace();
		}

		return null;
	}

	public static int[] parseName( final String name )
	{
		final int x_i = name.indexOf( searchX );
		final int y_i = name.indexOf( searchY, x_i);
		final int ch_i = name.indexOf( searchCh, y_i);
		final int obj_i = name.indexOf( searchObj, ch_i );

		final String x_string = name.substring(x_i+searchX.length(), y_i);
		final String y_string = name.substring(y_i+searchY.length(), ch_i);
		final String ch_string = name.substring(ch_i+searchCh.length(), obj_i);

		final int x = Integer.parseInt( x_string );
		final int y = Integer.parseInt( y_string );
		final int ch = Integer.parseInt( ch_string );

		return new int[] {x,y,ch};
	}

	public static final void main(final String... args) {
		new CommandLine( new RewriteLuxendoXML() ).execute( args );
	}
}
