package net.preibisch.mvrecon.fiji.spimdata.explorer;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import java.util.List;

public interface SelectedViewDescriptionListener< AS extends AbstractSpimData< ? > >
{
	//public void firstSelectedViewDescriptions( List< BasicViewDescription< ? extends BasicViewSetup > > viewDescriptions );
	public void selectedViewDescriptions( List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions );
	public void updateContent( final AS data );
	public void save();
	public void quit();
}
