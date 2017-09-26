package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import javax.swing.JComponent;

import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;

public interface ExplorerWindowSetable
{
	public JComponent setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel );
}
// AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >
