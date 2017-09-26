package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;

import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.plugin.Apply_Transformation;
import net.preibisch.mvrecon.fiji.plugin.apply.ApplyParameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class ApplyTransformationPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public ApplyTransformationPopup()
	{
		super( "Apply Transformation(s) ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public static final List< ViewId > getSelectedViews(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		return getSelectedViews( panel, true );
	}
	public static final List< ViewId > getSelectedViews(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel,
			final boolean filterMissing)
	{
		final List< ViewId > viewIds = new ArrayList<>();
		if (GroupedRowWindow.class.isInstance( panel ))
			((GroupedRowWindow)panel).selectedRowsViewIdGroups().forEach( vidsI -> viewIds.addAll( vidsI ) );
		else
			viewIds.addAll(panel.selectedRowsViewId());

		if (filterMissing)
			SpimData2.filterMissingViews( panel.getSpimData(), viewIds );
		return viewIds;
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

			if ( !SpimData.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final SpimData data = (SpimData)panel.getSpimData();

					final ArrayList< ViewId > viewIds = new ArrayList<>();
					viewIds.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( panel.getSpimData(), viewIds );

					final Apply_Transformation t = new Apply_Transformation();
		
					final ApplyParameters params = t.queryParams( data, viewIds );
		
					if ( params == null )
						return;
				
					final Map< ViewDescription, Pair< double[], String > > modelLinks;
				
					// query models and apply them
					if ( params.defineAs == 0 ) // matrix
						modelLinks = t.queryString( data, viewIds, params );
					else if ( params.defineAs == 1 ) //Rotation around axis
						modelLinks = t.queryRotationAxis( data, viewIds, params );
					else // Interactively using the BigDataViewer
						modelLinks = t.queryBigDataViewer( data, viewIds, params );
				
					if ( modelLinks == null )
						return;
				
					t.applyModels( data, params.minResolution, params.applyTo, modelLinks );
		
					// update registration panel if available
					panel.updateContent();
					panel.bdvPopup().updateBDV();
				}
			} ).start();
		}
	}
}
