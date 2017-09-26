package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import net.preibisch.mvrecon.fiji.plugin.Define_Bounding_Box;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class BoundingBoxPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ExplorerWindow< ?, ? > panel;

	public BoundingBoxPopup()
	{
		super( "Define Bounding Box ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JComponent setExplorerWindow( ExplorerWindow<? extends AbstractSpimData<? extends AbstractSequenceDescription<?, ?, ?>>, ?> panel )
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
					final ArrayList< ViewId > viewIds = new ArrayList<>();
					viewIds.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( panel.getSpimData(), viewIds );

					if ( new Define_Bounding_Box().defineBoundingBox( (SpimData2)panel.getSpimData(), viewIds ) != null )
					{
						panel.updateContent(); // update main table and registration panel if available
						panel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}
	}
}
