package net.preibisch.mvrecon.fiji.spimdata.boundingbox;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.base.XmlIoSingleton;

import static net.preibisch.mvrecon.fiji.spimdata.boundingbox.XmlKeysBoundingBox.BOUNDINGBOXES_TAG;
import static net.preibisch.mvrecon.fiji.spimdata.boundingbox.XmlKeysBoundingBox.BOUNDINGBOX_TAG;
import static net.preibisch.mvrecon.fiji.spimdata.boundingbox.XmlKeysBoundingBox.BOUNDINGBOX_TAG_MAX;
import static net.preibisch.mvrecon.fiji.spimdata.boundingbox.XmlKeysBoundingBox.BOUNDINGBOX_TAG_MIN;
import static net.preibisch.mvrecon.fiji.spimdata.boundingbox.XmlKeysBoundingBox.BOUNDINGBOX_TAG_NAME;

import org.jdom2.Element;

public class XmlIoBoundingBoxes extends XmlIoSingleton< BoundingBoxes >
{
	public XmlIoBoundingBoxes()
	{
		super( BOUNDINGBOXES_TAG, BoundingBoxes.class );
		handledTags.add( BOUNDINGBOX_TAG );
	}

	public Element toXml( final BoundingBoxes boundingBoxes )
	{
		final Element elem = super.toXml();

		for ( final BoundingBox bb : boundingBoxes.getBoundingBoxes() )
			elem.addContent( boundingBoxToXml( bb ) );

		return elem;
	}

	public BoundingBoxes fromXml( final Element allBoundingBoxes ) throws SpimDataException
	{
		final BoundingBoxes boundingBoxes = super.fromXml( allBoundingBoxes );

		for ( final Element boundingBoxElement : allBoundingBoxes.getChildren( BOUNDINGBOX_TAG ) )
		{
			final String title = boundingBoxElement.getAttributeValue( BOUNDINGBOX_TAG_NAME );

			final int[] min = XmlHelpers.getIntArray( boundingBoxElement, BOUNDINGBOX_TAG_MIN );
			final int[] max = XmlHelpers.getIntArray( boundingBoxElement, BOUNDINGBOX_TAG_MAX );

			boundingBoxes.addBoundingBox( new BoundingBox( title, min, max ) );
		}

		return boundingBoxes;
	}

	protected Element boundingBoxToXml( final BoundingBox bb )
	{
		final Element elem = new Element( BOUNDINGBOX_TAG );

		elem.setAttribute( BOUNDINGBOX_TAG_NAME, bb.getTitle() );
		elem.addContent( XmlHelpers.intArrayElement( BOUNDINGBOX_TAG_MIN, bb.getMin() ) );
		elem.addContent( XmlHelpers.intArrayElement( BOUNDINGBOX_TAG_MAX, bb.getMax() ) );
		
		return elem;
	}
}
