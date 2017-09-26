package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorer;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.registration.RegistrationExplorer;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.io.IOFunctions;

public class RegistrationExplorerPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	FilteredAndGroupedExplorerPanel< ?, ? > panel;
	RegistrationExplorer< ?, ? > re = null;

	public RegistrationExplorerPopup()
	{
		super( "Registration Explorer (on/off)" );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = (FilteredAndGroupedExplorerPanel< ?, ? >)panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( re == null || !re.frame().isVisible() )
					{
						re = instanceFor( (FilteredAndGroupedExplorerPanel)panel );
					}
					else
					{
						re.quit();
						re = null;
					}
				}
			}).start();
		}
	}

	private static final < AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > RegistrationExplorer< AS, X > instanceFor( final FilteredAndGroupedExplorerPanel< AS, X > panel )
	{
		return new RegistrationExplorer< AS, X >( panel.xml(), panel.io(), panel.explorer() );
	}
}
