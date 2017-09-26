package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.interestpoint.InterestPointExplorer;

import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.io.IOFunctions;

public class InterestPointsExplorerPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;
	InterestPointExplorer< ?, ? > ipe = null;

	public InterestPointsExplorerPopup()
	{
		super( "Interest Point Explorer (on/off)" );

		this.addActionListener( new MyActionListener() );
	}


	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = (ViewSetupExplorerPanel< ?, ? >)panel;
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

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( ipe == null || !ipe.frame().isVisible() )
					{
						ipe = instanceFor( (ViewSetupExplorerPanel)panel );
					}
					else
					{
						ipe.quit();
						ipe = null;
					}
				}
			}).start();
		}
	}

	private static final < AS extends SpimData2, X extends XmlIoAbstractSpimData< ?, AS > > InterestPointExplorer< AS, X > instanceFor( final FilteredAndGroupedExplorerPanel< AS, X > panel )
	{
		return new InterestPointExplorer< AS, X >( panel.xml(), panel.io(), panel.explorer() );
	}
}
