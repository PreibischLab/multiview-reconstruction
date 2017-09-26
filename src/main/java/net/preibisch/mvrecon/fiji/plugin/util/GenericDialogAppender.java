package net.preibisch.mvrecon.fiji.plugin.util;

import ij.gui.GenericDialog;

public interface GenericDialogAppender
{
	/*
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 */
	public void addQuery( final GenericDialog gd );
	
	/*
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @return
	 */
	public boolean parseDialog( final GenericDialog gd );

}
