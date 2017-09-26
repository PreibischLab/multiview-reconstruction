package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JMenuItem;

import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Registration;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class RegisterInterestPointsPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ExplorerWindow< ?, ? > panel;

	public RegisterInterestPointsPopup()
	{
		super( "Register using Interest Points ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;
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
					final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );

					// by default the registration suggests what is selected in the dialog
					Interest_Point_Registration.defaultGroupTiles = panel.tilesGrouped();
					Interest_Point_Registration.defaultGroupIllums = panel.illumsGrouped();
					Interest_Point_Registration.defaultGroupChannels = panel.channelsGrouped();

					if ( new Interest_Point_Registration().register( (SpimData2)panel.getSpimData(), viewIds ) )
					{
						panel.updateContent(); // update interestpoint and registration panel if available
						panel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}
	}
}
