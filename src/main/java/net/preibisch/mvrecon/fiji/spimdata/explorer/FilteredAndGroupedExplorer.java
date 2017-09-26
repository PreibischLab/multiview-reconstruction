package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.ArrayList;

import javax.swing.JFrame;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;

public abstract class FilteredAndGroupedExplorer<AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS >>
{

	protected JFrame frame;
	protected FilteredAndGroupedExplorerPanel< AS, X > panel;


	public AS getSpimData()
	{ return panel.getSpimData(); }

	public FilteredAndGroupedExplorerPanel< AS, X > getPanel()
	{ return panel; }

	public JFrame getFrame()
	{ return frame; }

	public void addListener(final SelectedViewDescriptionListener< AS > listener)
	{ panel.addListener( listener ); }

	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners()
	{ return panel.getListeners(); }

}