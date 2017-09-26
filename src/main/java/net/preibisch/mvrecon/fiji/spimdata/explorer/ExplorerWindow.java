package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BasicBDVPopup;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;

public interface ExplorerWindow< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > >
{
	public List< BasicViewDescription< ? extends BasicViewSetup > > selectedRows();
	public List< ViewId > selectedRowsViewId();
	public AS getSpimData();
	public void updateContent();
	public String xml();
	public void saveXML();

	public boolean tilesGrouped();
	public boolean illumsGrouped();
	public boolean channelsGrouped();

	// BDV-specific
	public BasicBDVPopup bdvPopup();
	public boolean colorMode();
	public BasicViewDescription< ? extends BasicViewSetup > firstSelectedVD();
}
