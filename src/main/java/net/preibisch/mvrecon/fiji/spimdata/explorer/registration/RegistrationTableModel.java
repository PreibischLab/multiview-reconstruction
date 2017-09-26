package net.preibisch.mvrecon.fiji.spimdata.explorer.registration;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;

public class RegistrationTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = -1263388435427674269L;

	ViewRegistrations viewRegistrations;
	final ArrayList< String > columnNames;
	final RegistrationExplorerPanel panel;
	
	BasicViewDescription< ? > currentVD;
	
	public RegistrationTableModel( final ViewRegistrations viewRegistrations, final RegistrationExplorerPanel panel )
	{
		this.columnNames = new ArrayList< String >();
		this.panel = panel;
		this.columnNames.add( "Transformation Name" );
		
		for ( int row = 0; row < 3; ++row )
			for ( int col = 0; col < 4; ++ col )
				this.columnNames.add( "m" + row + "" + col );
		
		this.viewRegistrations = viewRegistrations;
		this.currentVD = null;
	}

	protected void update( final ViewRegistrations viewRegistrations ) { this.viewRegistrations = viewRegistrations; }
	protected ViewRegistrations getViewRegistrations() { return viewRegistrations; }
	protected BasicViewDescription< ? > getCurrentViewDescription() { return currentVD; } 
	
	protected void updateViewDescription( final BasicViewDescription< ? > vd )
	{
		this.currentVD = vd;
		
		// update everything
		fireTableDataChanged();
	}

	@Override
	public int getColumnCount() { return columnNames.size(); }
	
	@Override
	public int getRowCount()
	{ 
		if ( currentVD == null )
			return 1;
		else
			return Math.max( 1, viewRegistrations.getViewRegistration( currentVD ).getTransformList().size() );
	}

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		// we can change name and the affine transform itself
		return true;
	}

	@Override
	public void setValueAt( final Object value, final int row, final int column )
	{
		final List< ViewTransform > vtList = viewRegistrations.getViewRegistration( currentVD ).getTransformList(); 
		final ViewTransform vt = vtList.get( row );
		
		if ( column == 0 )
		{
			final String newName = value.toString();
			vtList.set( row, RegistrationExplorerPanel.newName( vt, newName ) );
		}
		else
		{
			try
			{
				final double v = Double.parseDouble( value.toString() );
				vtList.set( row, RegistrationExplorerPanel.newMatrixEntry( vt, v, column - 1 ) );
			}
			catch ( Exception e )
			{
				System.out.println( "Cannot parse: " + value );
			}
		}
		
		viewRegistrations.getViewRegistration( currentVD ).updateModel();
		
		// do something ...
		panel.explorer.viewSetupExplorer.getPanel().bdvPopup().updateBDV();
		fireTableCellUpdated( row, column );
	}
	
	@Override
	public Object getValueAt( final int row, final int column )
	{
		if ( currentVD == null )
			return column == 0 ? "No View Description selected" : "";

		final ViewRegistration vr = viewRegistrations.getViewRegistration( currentVD );
		
		if ( vr.getTransformList().isEmpty() )
		{
			return column == 0 ? "No transformations present" : "";
		}
		else
		{
			if ( column == 0 )
				return vr.getTransformList().get( row ).getName();
			else
				return vr.getTransformList().get( row ).asAffine3D().getRowPackedCopy()[ column - 1 ];
		}
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}
}
