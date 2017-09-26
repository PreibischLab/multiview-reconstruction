package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import net.preibisch.mvrecon.fiji.plugin.util.MultiWindowLayoutHelper;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BasicBDVPopup;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;

public class ViewSetupExplorer< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > extends FilteredAndGroupedExplorer< AS, X >
{
	public static final double xPos = 0.4;
	public static final double yPos = 0.4;
	public static final double xPosLog = 0.0;
	public static final double yPosLog = 0.8;

	public ViewSetupExplorer( final AS data, final String xml, final X io )
	{
		frame = new JFrame( "ViewSetup Explorer" );
		panel = new ViewSetupExplorerPanel< AS, X >( this, data, xml, io, true );

		frame.add( panel, BorderLayout.CENTER );
		frame.setSize( panel.getPreferredSize() );

		frame.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing( WindowEvent evt )
					{
						quit();
					}
				});

		frame.pack();
		frame.setVisible( true );

		// move explorer window and log to initial positions
		MultiWindowLayoutHelper.moveToScreenFraction( frame, xPos, yPos );
		MultiWindowLayoutHelper.moveToScreenFraction( MultiWindowLayoutHelper.getIJLogWindow(), xPosLog, yPosLog );

		// set the initial focus to the table
		panel.table.requestFocus();
	}
	
	public void quit()
	{
		for ( final SelectedViewDescriptionListener< AS > l : panel.getListeners() )
			l.quit();

		panel.getListeners().clear();

		frame.setVisible( false );
		frame.dispose();

		BasicBDVPopup bdvPopup = panel.bdvPopup();
		
		if ( bdvPopup.bdvRunning() )
			bdvPopup.closeBDV();

		ViewSetupExplorerPanel.currentInstance = null;
	}
}
