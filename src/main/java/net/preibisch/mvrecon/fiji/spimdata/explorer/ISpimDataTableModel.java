package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.table.TableModel;

import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;

public interface ISpimDataTableModel<AS extends AbstractSpimData<?>> extends TableModel {
	
	public enum SpecialColumnType{
		INTEREST_POINT_COLUMN,
		VIEW_REGISTRATION_COLUMN
	}
	
	public int getSpecialColumn(SpecialColumnType type);
	
	public ExplorerWindow< AS, ? > getPanel();
	
	public Set<Class<? extends Entity>> getGroupingFactors();
	
	public void clearSortingFactors();

	public void addSortingFactor(Class<? extends Entity> factor);

	public void clearGroupingFactors();

	public void addGroupingFactor(Class<? extends Entity> factor);

	public void clearFilters();

	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances);
	
	public Map<Class<? extends Entity> , List<? extends Entity>> getFilters();
	
	public List<List< BasicViewDescription< ?  >> > getElements();
	
	public void sortByColumn( final int column );
	
	public void setColumnClasses(List< Class< ? extends Entity > > columnClasses);

}