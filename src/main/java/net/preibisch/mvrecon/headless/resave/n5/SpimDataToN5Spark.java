package net.preibisch.mvrecon.headless.resave.n5;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.spark.supplier.N5WriterSupplier;
import org.janelia.saalfeldlab.n5.spark.util.N5Compression;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.headless.resave.HeadlessParseQueryXML;

public class SpimDataToN5Spark
{

	public static < T extends NativeType< T > > void convert(
			final JavaSparkContext sparkContext,
			final String inputXml,
			final N5WriterSupplier outputN5Supplier,
			//final String outputDataset,
			//final int[] blockSize,
			final Compression compression ) throws IOException
	{
		
		HeadlessParseQueryXML xml_ = new HeadlessParseQueryXML();
		xml_.loadXML( inputXml, false );
		SpimData2 data = xml_.getData();
		
		List< ViewId > vids = data.getSequenceDescription().getViewDescriptions().keySet().stream().collect( Collectors.toList() );
		
		sparkContext.parallelize( vids ).foreach( i -> {
			N5Writer n5Writer = outputN5Supplier.get();
			
			HeadlessParseQueryXML xml = new HeadlessParseQueryXML();
			xml.loadXML( inputXml, false );
			SpimData2 data_ = xml.getData();
			
			Dimensions size = data_.getSequenceDescription().getViewDescriptions().get( i ).getViewSetup().getSize();
			long[] dims = new long[size.numDimensions()];
			size.dimensions( dims );
			
			RandomAccessibleInterval< T > image = (RandomAccessibleInterval< T >) data_.getSequenceDescription().getImgLoader().getSetupImgLoader( i.getViewSetupId() ).getImage( i.getTimePointId() );
			
			//n5Writer.createDataset( String.format( "/%02d/%05d", i.getViewSetupId(), i.getTimePointId() ), dims, new int[] {64,64,64}, DataType.INT16, compression );
			N5Utils.save( image, n5Writer, String.format( "/setup%02d/timepoint%05d", i.getViewSetupId(), i.getTimePointId() ), new int[] {64,64,64}, compression );
		});
	}
	
	public static void main(String[] args) throws IOException
	{
		try ( final JavaSparkContext sparkContext = new JavaSparkContext( new SparkConf()
				.setMaster( "local[*]" )
				.setAppName( "SliceTiffToN5Spark" )
				.set( "spark.serializer", "org.apache.spark.serializer.KryoSerializer" )
			) )
		{
			final N5WriterSupplier n5Supplier = () -> new N5FSWriter( "/Users/david/Desktop/testn52.n5" );
			convert(
					sparkContext,
					"/Users/david/Desktop/grid-3d/dataset.xml",
					n5Supplier,
					//parsedArgs.getOutputDatasetPath(),
					//parsedArgs.getBlockSize(),
					N5Compression.GZIP.get() //parsedArgs.getCompression()
				);
		}

		System.out.println( System.lineSeparator() + "Done" );
	}
}
