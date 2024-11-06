package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import java.io.File;
import java.util.concurrent.Future;

import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.img.n5.N5ImageLoader;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.InitializeViewerState;
import bdv.ui.UIUtils;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimDataException;

public class AllenOMEZarrTest
{
	public static void main( String[] args ) throws SpimDataException
	{
		final String fn = "/Users/pietzsch/Desktop/data/Allen/704522.xml";

		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		UIUtils.installFlatLafInfos();

		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( fn );
		final N5ImageLoader n5ImageLoader = ( N5ImageLoader ) spimData.getSequenceDescription().getImgLoader();
		final Future< Void > f = n5ImageLoader.prefetch( 256 );
		n5ImageLoader.setNumFetcherThreads( 64 );

		final BigDataViewer bdv = BigDataViewer.open( spimData, new File( fn ).getName(), new ProgressWriterConsole(), ViewerOptions.options() );
		InitializeViewerState.initBrightness( 0, 1, bdv.getViewerFrame() );
	}
}
