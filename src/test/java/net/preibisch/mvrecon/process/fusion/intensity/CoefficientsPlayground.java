package net.preibisch.mvrecon.process.fusion.intensity;

import static net.imglib2.util.Util.safeInt;

import java.io.File;
import java.net.URI;
import java.util.Random;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.DoubleArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;

import net.imglib2.util.Intervals;
import util.URITools;

public class CoefficientsPlayground {

	public static void main(String[] args) {
		final Coefficients coefficients = createCoefficients();

		final URI uri = new File("/Users/pietzsch/Desktop/coefficients.n5").toURI();
		final N5Writer n5Writer = URITools.instantiateN5Writer(StorageFormat.N5, uri);

		String dataset = "coefficients";
		writeCoefficients(n5Writer, dataset, coefficients);

		n5Writer.close();
	}


	static void writeCoefficients(final N5Writer n5Writer, final String datasetPath, final Coefficients coefficients) {

		if (n5Writer.exists(datasetPath))
			n5Writer.remove(datasetPath);

		final int n = coefficients.numDimensions();
		final long[] dimensions = new long[n + 1];
		final int[] blockSize = new int[n + 1];
		for (int d = 0; d < n; d++) {
			dimensions[d] = coefficients.size(d);
			blockSize[d] = coefficients.size(d);
		}
		dimensions[n] = coefficients.numCoefficients();
		blockSize[n] = 1;
		final DatasetAttributes attr = new DatasetAttributes(dimensions, blockSize, DataType.FLOAT64, new RawCompression());
		n5Writer.createDataset(datasetPath, attr);
		n5Writer.setAttribute(datasetPath, "coefficients version", "1.0");

		final long[] gridPosition = new long[n + 1];
		for (int i = 0; i < coefficients.numCoefficients(); ++i) {
			gridPosition[n] = i;
			final DoubleArrayDataBlock block = new DoubleArrayDataBlock(blockSize, gridPosition, coefficients.flattenedCoefficients[i]);
			n5Writer.writeBlock(datasetPath, attr, block);
		}
	}

	// Create 8x8x8 affine1D coefficient field,
	// with every affine1D having random scale (0.5, 1.5), and random offset [-100, 100]
	static Coefficients createCoefficients() {
		final int[] fieldDimensions = {8, 8, 8};
		final int numElements = safeInt(Intervals.numElements(fieldDimensions));
		final double[][] coeffData = new double[2][numElements];
		final Random random = new Random(1);
		for (int i = 0; i < numElements; ++i) {
			coeffData[0][i] = 0.5 + random.nextDouble();
			coeffData[1][i] = random.nextInt(200) - 100;
		}
		return new Coefficients(coeffData, fieldDimensions);
	}
}
