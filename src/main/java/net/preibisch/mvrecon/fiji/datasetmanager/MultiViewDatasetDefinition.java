package net.preibisch.mvrecon.fiji.datasetmanager;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

import mpicbg.spim.data.SpimData;

public interface MultiViewDatasetDefinition
{
	/**
	 * Defines the title under which it will be displayed in the list
	 * of available multi-view dataset definitions
	 * 
	 * @return the title
	 */
	public String getTitle();
	
	/**
	 * An explanation for the user what exactly this {@link MultiViewDatasetDefinition}
	 * supports and how it needs to be stored. Up to 15 lines will be displayed with
	 * 80 characters each. No newline characters are allowed.
	 * 
	 * @return description
	 */
	public String getExtendedDescription();
	
	/**
	 * This method is supposed to (interactively, ideally ImageJ-macroscriptable)
	 * query all necessary data from the user to build up a SpimData object and
	 * save it as an XML file.
	 * 
	 * @return - the saved {@link SpimData} object
	 */
	public SpimData2 createDataset();
	
	/**
	 * @return - a new instance of this implementation
	 */
	public MultiViewDatasetDefinition newInstance();
}
