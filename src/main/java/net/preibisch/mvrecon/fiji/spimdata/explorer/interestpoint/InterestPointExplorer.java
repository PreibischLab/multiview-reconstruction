package net.preibisch.mvrecon.fiji.spimdata.explorer.interestpoint;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorer;
import net.preibisch.mvrecon.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BasicBDVPopup;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;

import bdv.BigDataViewer;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class InterestPointExplorer< AS extends SpimData2, X extends XmlIoAbstractSpimData< ?, AS > >
	implements SelectedViewDescriptionListener< AS >
{
	final String xml;
	final JFrame frame;
	final InterestPointExplorerPanel panel;
	final FilteredAndGroupedExplorer< AS, X > viewSetupExplorer;

	public InterestPointExplorer( final String xml, final X io, final FilteredAndGroupedExplorer< AS, X > viewSetupExplorer )
	{
		this.xml = xml;
		this.viewSetupExplorer = viewSetupExplorer;

		frame = new JFrame( "Interest Point Explorer" );
		panel = new InterestPointExplorerPanel( viewSetupExplorer.getPanel().getSpimData().getViewInterestPoints(), viewSetupExplorer );
		frame.add( panel, BorderLayout.CENTER );

		frame.setSize( panel.getPreferredSize() );

		frame.pack();
		frame.setVisible( true );
		
		// Get the size of the screen
		final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		// Move the window
		frame.setLocation( ( dim.width - frame.getSize().width ) / 2, ( dim.height - frame.getSize().height ) / 4 );

		// this call also triggers the first update of the registration table
		viewSetupExplorer.addListener( this );

		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				quit();
				e.getWindow().dispose();
			}
		});
	}

	public JFrame frame() { return frame; }

	@Override
	public void selectedViewDescriptions( final List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions )
	{
		final ArrayList< BasicViewDescription< ? extends BasicViewSetup > > fullList = new ArrayList<>();

		for ( final List< BasicViewDescription< ? extends BasicViewSetup > > list : viewDescriptions )
			for ( final BasicViewDescription< ? extends BasicViewSetup > vd : list )
				if ( vd.isPresent() )
					fullList.add( vd );

		panel.updateViewDescription( fullList );
	}

	@Override
	public void save()
	{
		for ( final Pair< InterestPointList, ViewId > list : panel.delete )
		{
			IOFunctions.println( "Deleting correspondences and interestpoints in timepointid=" + list.getB().getTimePointId() + ", viewid=" + list.getB().getViewSetupId() );

			final File ip = new File( list.getA().getBaseDir(), list.getA().getFile().toString() + list.getA().getInterestPointsExt() );
			final File corr = new File( list.getA().getBaseDir(), list.getA().getFile().toString() + list.getA().getCorrespondencesExt() );

			if ( ip.delete() )
				IOFunctions.println( "Deleted: " + ip.getAbsolutePath() );
			else
				IOFunctions.println( "FAILED to delete: " + ip.getAbsolutePath() );

			if ( corr.delete() )
				IOFunctions.println( "Deleted: " + corr.getAbsolutePath() );
			else
				IOFunctions.println( "FAILED to delete: " + corr.getAbsolutePath() );
		}

		//panel.save.clear();
		panel.delete.clear();
	}

	@Override
	public void quit()
	{
		final BasicBDVPopup bdvPopup = viewSetupExplorer.getPanel().bdvPopup();

		if ( bdvPopup.bdvRunning() && panel.tableModel.interestPointOverlay != null )
		{
			final BigDataViewer bdv = bdvPopup.getBDV();
			bdv.getViewer().removeTransformListener( panel.tableModel.interestPointOverlay );
			bdv.getViewer().getDisplay().removeOverlayRenderer( panel.tableModel.interestPointOverlay );
			bdvPopup.updateBDV();
		}

		frame.setVisible( false );
		frame.dispose();
	}

	public InterestPointExplorerPanel panel() { return panel; }

	@Override
	public void updateContent( final AS data )
	{
		panel.getTableModel().update( data.getViewInterestPoints() );
		panel.getTableModel().fireTableDataChanged();
	}
}
