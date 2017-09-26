package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import bdv.BigDataViewer;

public interface BasicBDVPopup
{
	public void updateBDV();
	public BigDataViewer getBDV();
	public boolean bdvRunning();
	public void closeBDV();
}
