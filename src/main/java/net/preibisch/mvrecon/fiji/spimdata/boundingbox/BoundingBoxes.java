package net.preibisch.mvrecon.fiji.spimdata.boundingbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BoundingBoxes
{
	private List< BoundingBox > boundingBoxes;

	public BoundingBoxes()
	{
		this.boundingBoxes = new ArrayList< BoundingBox >();
	}

	public BoundingBoxes( final Collection< BoundingBox > boundingBoxes )
	{
		this();
		this.boundingBoxes.addAll( boundingBoxes );
	}

	public List< BoundingBox > getBoundingBoxes() { return boundingBoxes; }
	public void addBoundingBox( final BoundingBox box ) { this.boundingBoxes.add( box ); }
}
