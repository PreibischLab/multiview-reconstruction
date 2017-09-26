package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import javax.swing.JSeparator;

import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

public class Separator extends JSeparator implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	@Override
	public JSeparator setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		return this;
	}
}
