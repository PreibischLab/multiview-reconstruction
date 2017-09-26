package net.preibisch.mvrecon.fiji.spimdata.imgloaders;

import static mpicbg.spim.data.XmlHelpers.loadPath;
import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

import java.io.File;
import java.util.HashMap;

import org.jdom2.Element;

import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

@ImgLoaderIo( format = "spimreconstruction.filelist", type = FileMapImgLoaderLOCI.class )
public class XmlIoFileListImgLoaderLOCI implements XmlIoBasicImgLoader< FileMapImgLoaderLOCI >
{

	public static final String DIRECTORY_TAG = "imagedirectory";
	public static final String FILES_TAG = "files";
	public static final String FILE_MAPPING_TAG = "FileMapping";
	public static final String IMGLIB2CONTAINER_PATTERN_TAG = "imglib2container";
	public static final String MAPPING_VS_TAG = "view_setup";
	public static final String MAPPING_TP_TAG = "timepoint";
	public static final String MAPPING_FILE_TAG = "file";
	public static final String MAPPING_SERIES_TAG = "series";
	public static final String MAPPING_C_TAG = "channel";
	
	@Override
	public Element toXml(FileMapImgLoaderLOCI imgLoader, File basePath)
	{
		HashMap< BasicViewDescription< ? >, Pair< File, Pair< Integer, Integer > > > fileMap = imgLoader.getFileMap();
		
		final Element wholeElem = new Element( "ImageLoader" );
		wholeElem.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass().getAnnotation( ImgLoaderIo.class ).format() );
		wholeElem.addContent( XmlHelpers.textElement( IMGLIB2CONTAINER_PATTERN_TAG, imgLoader.getImgFactory().getClass().getSimpleName() ) );
		
		final Element filesElement = new Element( FILES_TAG );
		
		for (BasicViewDescription< ? > vs : fileMap.keySet())
		{
			final Pair< File, Pair< Integer, Integer > > pair = fileMap.get( vs );
			final Element fileMappingElement = new Element( FILE_MAPPING_TAG );
			fileMappingElement.setAttribute( MAPPING_VS_TAG, Integer.toString( vs.getViewSetupId()) );
			fileMappingElement.setAttribute( MAPPING_TP_TAG, Integer.toString( vs.getTimePointId()) );
			fileMappingElement.addContent( XmlHelpers.pathElement( MAPPING_FILE_TAG, pair.getA(), basePath ) );
			fileMappingElement.setAttribute( MAPPING_SERIES_TAG, Integer.toString(pair.getB().getA()) );
			fileMappingElement.setAttribute( MAPPING_C_TAG, Integer.toString(pair.getB().getB()) );
			
			filesElement.addContent( fileMappingElement );
		}
		
		//elem.addContent( XmlHelpers.pathElement( DIRECTORY_TAG, imgLoader.getCZIFile().getParentFile(), basePath ) );
		//wholeElem.addContent( XmlHelpers.textElement( MASTER_FILE_TAG, imgLoader.getCZIFile().getName() ) );
		
		wholeElem.addContent( filesElement );
		
		return wholeElem;
	}

	@Override
	public FileMapImgLoaderLOCI fromXml(Element elem, File basePath,
			AbstractSequenceDescription< ?, ?, ? > sequenceDescription)
	{
		//final File path = loadPath( elem, DIRECTORY_TAG, basePath );		
		final Element fileMapElement = elem.getChild( FILES_TAG );		
		final String container = XmlHelpers.getText( elem, IMGLIB2CONTAINER_PATTERN_TAG );

		final ImgFactory< FloatType > imgFactory;

		if ( container == null )
		{
			System.out.println( "WARNING: No Img implementation defined in XML, using ArrayImg." );

			// if no factory is defined we define an ArrayImgFactory
			imgFactory = new ArrayImgFactory< FloatType >();
		}
		else
		{
			if ( container.toLowerCase().contains( "cellimg" ) )
			{
				imgFactory = new CellImgFactory< FloatType >( 256 );
			}
			else if ( container.toLowerCase().contains( "arrayimg" ) )
			{
				imgFactory = new ArrayImgFactory< FloatType >();
			}
			else if ( container.toLowerCase().contains( "planarimg" ) )
			{
				imgFactory = new PlanarImgFactory< FloatType >();
			}
			else
			{
				// if factory is unknown we define an ArrayImgFactory
				imgFactory = new ArrayImgFactory< FloatType >();
				
				System.out.println( "WARNING: Unknown Img implementation defined in XML:'" + container + "', using ArrayImg." );
			}
		}
		
		final HashMap< BasicViewDescription< ? >, Pair< File, Pair< Integer, Integer > > > fileMap = new HashMap<>();
		
		for (Element e : fileMapElement.getChildren( FILE_MAPPING_TAG )){
			int vs = Integer.parseInt( e.getAttribute( MAPPING_VS_TAG ).getValue());
			int tp = Integer.parseInt( e.getAttribute( MAPPING_TP_TAG ).getValue());
			int series = Integer.parseInt( e.getAttribute( MAPPING_SERIES_TAG ).getValue());
			int channel = Integer.parseInt( e.getAttribute( MAPPING_C_TAG ).getValue());
			File f = XmlHelpers.loadPath( e, MAPPING_FILE_TAG, basePath );
			
			BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( new ViewId( tp, vs ) );
			Pair< File, Pair< Integer, Integer > > p = new ValuePair< File, Pair<Integer,Integer> >( f, new ValuePair< Integer, Integer >( series, channel ) );
			
			fileMap.put( vd, p );
		}
		
		return new FileMapImgLoaderLOCI( fileMap, imgFactory, sequenceDescription );
	}

}
