package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RealPoint;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class LogClickLocationPopup extends JMenuItem implements ExplorerWindowSetable
{

	// default serialVersionUID
	private static final long serialVersionUID = 1L;

	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	private LogClickMouseAdapter mouseAdapter;
	private boolean active = false;

	private class LogClickMouseAdapter extends MouseAdapter
	{
		@Override
		public void mouseClicked(MouseEvent e)
		{
			// global coordinates of clicked point
			final RealPoint gPos = new RealPoint( 3 );
			panel.bdvPopup().getBDV().getViewerFrame().getViewerPanel().getGlobalMouseCoordinates( gPos );

			IOFunctions.println( "---" ); // separate clicks in log

			// go through all vid, registration pairs
			final ViewRegistrations viewRegistrations = panel.getSpimData().getViewRegistrations();
			viewRegistrations.getViewRegistrations().forEach( ( vid, vr) -> {

				// to pixel coordinates
				final RealPoint pixPos = new RealPoint( 3 );
				vr.getModel().applyInverse( pixPos, gPos );

				// view has to be selected and present
				if (!panel.getSpimData().getSequenceDescription().getMissingViews().getMissingViews().contains( vid ) &&
						((GroupedRowWindow)panel).selectedRowsViewIdGroups().stream().reduce( false, (b,v) -> b || v.contains(vid), (b1,b2) -> b1 || b2 ))
				{
					// view setup has to have size
					BasicViewSetup viewSetup = panel.getSpimData().getSequenceDescription().getViewDescriptions().get( vid ).getViewSetup();
					if (viewSetup.hasSize())
					{
						// check that pixel coordinates are in raw image
						for (int d=0; d<3; d++)
						{
							if (pixPos.getFloatPosition( d ) < 0 || pixPos.getFloatPosition( d ) > viewSetup.getSize().dimension( d ) - 1)
								return;
						}

						// log global and pixel coordinates
						IOFunctions.println(Group.pvid( vid ) + "--- global: " + Util.printCoordinates( gPos ) + "--- pixel: " + Util.printCoordinates( pixPos ));
					}
				}
			});
			
		}
	}

	private class LogClickPopupActionListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			if (!active && panel.bdvPopup().bdvRunning())
			{
				active = true;
				panel.bdvPopup().getBDV().getViewer().getDisplay().addHandler( mouseAdapter );
				IOFunctions.println( "Started logging click locations" );

				panel.bdvPopup().getBDV().getViewerFrame().addWindowListener( new WindowAdapter()
				{
					@Override
					public void windowClosing(java.awt.event.WindowEvent e) {
						panel.bdvPopup().getBDV().getViewer().getDisplay().removeHandler( mouseAdapter );
						active = false;
						IOFunctions.println( "Stopped logging click locations" );
					};
				} );
			}

			else if (active && panel.bdvPopup().bdvRunning())
			{
				panel.bdvPopup().getBDV().getViewer().getDisplay().removeHandler( mouseAdapter );
				active = false;
				IOFunctions.println( "Stopped logging click locations" );
			}
		}
	}


	public LogClickLocationPopup()
	{
		super("Log BDV click location (on/off)");
		this.addActionListener(new LogClickPopupActionListener());
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		this.mouseAdapter = new LogClickMouseAdapter();
		return this;
	}


}
