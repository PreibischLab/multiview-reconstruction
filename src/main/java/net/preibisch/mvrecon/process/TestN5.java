package net.preibisch.mvrecon.process;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.ImageJ;
import javassist.expr.NewArray;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.ViewSetups;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.display.screenimage.awt.UnsignedShortAWTScreenImage;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;

public class TestN5
{
	public static class BasePath {
		
		private String path;
		private String type;
	}
	
	public static class ViewSetupList {
		
		private List<ViewSetup> viewSetups;
		private List<Illumination> illuminations;
		private List<Tile> tiles;
		private List<Angle> angles;
		private List<Channel> channels;
	}
	
	public static class SpimData2Export {
		
		private String version;
		private BasePath basePath;
		private ViewSetupList viewSetups;
	}
	
	public static class DimensionsAdapter implements JsonDeserializer<Dimensions>, JsonSerializer<Dimensions> {

		@Override
		public Dimensions deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			final long[] dims = new long[json.getAsJsonArray().size()];
			Arrays.setAll(dims, i -> json.getAsJsonArray().get(i).getAsLong());
			return new FinalDimensions(dims);
		}

		@Override
		public JsonElement serialize(Dimensions src, Type typeOfSrc, JsonSerializationContext context) {
			// TODO Auto-generated method stub
			return context.serialize(Intervals.dimensionsAsLongArray(src));
		}
	}
	
	public static class VoxelDimensionsAdapter implements JsonDeserializer<VoxelDimensions>, JsonSerializer<VoxelDimensions> {

		@Override
		public VoxelDimensions deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			JsonElement element = json.getAsJsonObject().get("dimensions");
			final double[] array = new double[element.getAsJsonArray().size()];
			Arrays.setAll(array, i -> element.getAsJsonArray().get(i).getAsDouble());
			String unit = json.getAsJsonObject().get("unit").getAsString();
			
			return new FinalVoxelDimensions(unit, array);
		}

		@Override
		public JsonElement serialize(VoxelDimensions src, Type typeOfSrc, JsonSerializationContext context) {
			return context.serialize(src);
		}
	}

	public static void main( String[] args ) throws SpimDataException, IOException, InterruptedException, ExecutionException
	{
//		new ImageJ();

		SpimData2 spimData;

		// load drosophila
		if ( System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		else
			spimData = new XmlIoSpimData2( "" ).load( "/home/steffi/Desktop/HisYFP-SPIM/dataset.xml" );

		final GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Dimensions.class, new DimensionsAdapter());
		gsonBuilder.registerTypeAdapter(VoxelDimensions.class, new VoxelDimensionsAdapter());

		N5FSWriter n5 = new N5FSWriter(spimData.getBasePath().getAbsolutePath() + "/test.n5", gsonBuilder);
		n5.createGroup("/whatever");
		
				
		
		SpimData2Export spimData2Export = new SpimData2Export();
		spimData2Export.version = "2.0.2";
		spimData2Export.basePath = new BasePath();
		spimData2Export.basePath.path = spimData.getBasePath().getAbsolutePath();
		spimData2Export.basePath.type = "absolute";
		
		spimData2Export.viewSetups = new ViewSetupList();
		spimData2Export.viewSetups.viewSetups = new ArrayList<ViewSetup>();
		spimData2Export.viewSetups.viewSetups.addAll( spimData.getSequenceDescription().getViewSetups().values() );
		spimData2Export.viewSetups.angles = new ArrayList<>();
		spimData2Export.viewSetups.angles.addAll( spimData.getSequenceDescription().getAllAnglesOrdered() );
		for ( final Angle a : spimData2Export.viewSetups.angles )
			a.setRotation(new double[] { 1, 0, 0 }, Double.parseDouble( a.getName() ) );
		spimData2Export.viewSetups.illuminations = new ArrayList<>();
		spimData2Export.viewSetups.illuminations.addAll( spimData.getSequenceDescription().getAllIlluminationsOrdered() );
		spimData2Export.viewSetups.tiles = new ArrayList<>();
		spimData2Export.viewSetups.tiles.addAll( spimData.getSequenceDescription().getAllTilesOrdered() );
		spimData2Export.viewSetups.channels = new ArrayList<>();
		spimData2Export.viewSetups.channels.addAll( spimData.getSequenceDescription().getAllChannelsOrdered() );
		
		n5.setAttribute("/whatever", "theData1", spimData2Export);
		n5.setAttribute("/whatever", "theData", null);
		
		System.out.println(n5.getAttribute("/whatever", "theData", SpimData2Export.class));
		System.out.println(n5.getAttribute("/whatever", "theData1", SpimData2Export.class));

		System.out.println("Done.");

		
		// load HDF5 using N5 tools
		// check with " h5ls -rv /home/steffi/Desktop/HisYFP-SPIM/dataset.h5"
		IHDF5Reader hdf5Reader = HDF5Factory.openForReading("/home/steffi/Desktop/HisYFP-SPIM/dataset.h5");
		N5HDF5Reader n5Hdf5Reader = new N5HDF5Reader(hdf5Reader, new int[]{64, 64, 64});
		final RandomAccessibleInterval<UnsignedShortType> img = N5Utils.openVolatile(n5Hdf5Reader, "/t00000/s00/0/cells");
		
		BdvOptions o = BdvOptions.options();
		BdvStackSource d = BdvFunctions.show(VolatileViews.wrapAsVolatile(img), "/t00000/s00/0/cells");
		d.setDisplayRange(0, 255);
		
		ExecutorService exec = Executors.newFixedThreadPool(16);
		N5Utils.save(img, n5, "/whatever/dataset", new int[]{32, 32, 32}, new GzipCompression(6), exec);
		exec.shutdown();
		
		System.out.println("Saved");
		
		o.addTo( d );
		BdvFunctions.show(VolatileViews.wrapAsVolatile((RandomAccessibleInterval)N5Utils.openVolatile(n5, "/whatever/dataset")), "/whatever/dataset", o );
		n5.setAttribute("/whatever/dataset", "transform", new double[] {1, 0 ,0 ,0, 0, 1, 0, 0, 0, 0, 0, 1, 0});
	}
}
