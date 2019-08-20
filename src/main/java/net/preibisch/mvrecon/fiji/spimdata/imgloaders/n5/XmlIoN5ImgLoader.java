package net.preibisch.mvrecon.fiji.spimdata.imgloaders.n5;

import static mpicbg.spim.data.XmlHelpers.loadPath;
import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

import java.io.File;

import org.jdom2.Element;

import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;

@ImgLoaderIo( format = "spimreconstruction.n5", type = N5ImgLoader.class )
public class XmlIoN5ImgLoader implements XmlIoBasicImgLoader< N5ImgLoader >
{
	private static final String CONTAINER_PATH_TAG = "containerPath";

	@Override
	public Element toXml(N5ImgLoader imgLoader, File basePath)
	{
		final Element elem = new Element( "ImageLoader" );
		elem.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass().getAnnotation( ImgLoaderIo.class ).format() );
		elem.addContent( XmlHelpers.pathElement( CONTAINER_PATH_TAG, new File(imgLoader.getContainerPath()), basePath ) );
		
		return elem;
	}

	@Override
	public N5ImgLoader fromXml(Element elem, File basePath, AbstractSequenceDescription< ?, ?, ? > sequenceDescription)
	{
		final File path = loadPath( elem, CONTAINER_PATH_TAG, basePath );
		return new N5ImgLoader( path.getAbsolutePath(), sequenceDescription );
	}

}
