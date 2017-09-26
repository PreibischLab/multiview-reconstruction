package net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions;

import static net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSFS_TAG;
import static net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_FILE_TAG;
import static net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_SETUP_ATTRIBUTE_NAME;
import static net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_TAG;
import static net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.XmlKeysPointSpreadFunctions.PSF_TIMEPOINT_ATTRIBUTE_NAME;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import org.jdom2.Element;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.base.XmlIoSingleton;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

public class XmlIoPointSpreadFunctions extends XmlIoSingleton< PointSpreadFunctions >
{
	public XmlIoPointSpreadFunctions()
	{
		super( PSFS_TAG, PointSpreadFunctions.class );
		handledTags.add( PSF_TAG );
	}

	public Element toXml( final PointSpreadFunctions pointSpreadFunctions )
	{
		final Element elem = super.toXml();

		// sort all entries by timepoint and viewsetupid so that it is possible to edit XML by hand
		final ArrayList< ViewId > views = new ArrayList<>();
		views.addAll( pointSpreadFunctions.getPointSpreadFunctions().keySet() );
		Collections.sort( views );

		for ( final ViewId v : views )
			elem.addContent( psfToXml( pointSpreadFunctions.getPointSpreadFunctions().get( v ), v ) );

		return elem;
	}

	public PointSpreadFunctions fromXml( final Element allPSFs, final File basePath ) throws SpimDataException
	{
		final PointSpreadFunctions pointSpreadFunctions = super.fromXml( allPSFs );

		for ( final Element psfElement : allPSFs.getChildren( PSF_TAG ) )
		{
			final int tpId = Integer.parseInt( psfElement.getAttributeValue( PSF_TIMEPOINT_ATTRIBUTE_NAME ) );
			final int vsId = Integer.parseInt( psfElement.getAttributeValue( PSF_SETUP_ATTRIBUTE_NAME ) );

			final String file = psfElement.getChildText( PSF_FILE_TAG );

			pointSpreadFunctions.addPSF( new ViewId( tpId, vsId ), new PointSpreadFunction( basePath, file ) );
		}

		return pointSpreadFunctions;
	}

	protected Element psfToXml( final PointSpreadFunction psf, final ViewId viewId )
	{
		final Element elem = new Element( PSF_TAG );

		elem.setAttribute( PSF_TIMEPOINT_ATTRIBUTE_NAME, Integer.toString( viewId.getTimePointId() ) );
		elem.setAttribute( PSF_SETUP_ATTRIBUTE_NAME, Integer.toString( viewId.getViewSetupId() ) );

		elem.addContent( new Element( PSF_FILE_TAG ).addContent( psf.getFile() ) );

		if ( psf.isModified() )
		{
			if ( !psf.save() )
				IOFunctions.println( "ERROR: Could not save PSF '" + psf.getFile() + "'" );
			else
				IOFunctions.println( "Saved PSF '" + psf.getFile() + "'" );
		}

		return elem;
	}
}
