package net.preibisch.mvrecon.process.fusion.intensity;

import bdv.AbstractSpimSource;
import bdv.BigDataViewer;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.InitializeViewerState;
import bdv.tools.transformation.TransformedSource;
import bdv.ui.UIUtils;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.viewer.render.DefaultMipmapOrdering;
import bdv.viewer.render.MipmapOrdering;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.algorithm.blocks.BlockSupplier;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.fluent.RandomAccessibleIntervalView;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import org.fife.ui.rsyntaxtextarea.modes.JsonTokenMaker;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import util.URITools;

import static bdv.viewer.Interpolation.NEARESTNEIGHBOR;
import static net.imglib2.view.fluent.RandomAccessibleIntervalView.Extension.zero;
import static net.imglib2.view.fluent.RandomAccessibleView.Interpolation.clampingNLinear;
import static net.imglib2.view.fluent.RandomAccessibleView.Interpolation.nearestNeighbor;

public class IntensityVisualizationPlayground {


	public static void main(String[] args) throws URISyntaxException, SpimDataException {
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		UIUtils.installFlatLafInfos();

		final URI datasetUri = new URI("file:/Users/pietzsch/Desktop/data/Janelia/keller-shadingcorrected/dataset.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(datasetUri);

		WrapBasicImgLoader.wrapImgLoaderIfNecessary(spimData);
		final AbstractSequenceDescription<?, ?, ?> seq = spimData.getSequenceDescription();
		final int numTimepoints = seq.getTimePoints().size();
		final ArrayList<SourceAndConverter<?>> sources = new ArrayList<>();
		BigDataViewer.initSetups(spimData, new ArrayList<>(), sources);
		WrapBasicImgLoader.removeWrapperIfPresent(spimData);





		final URI coefficientsUri = new File("/Users/pietzsch/Desktop/intensity.n5").toURI();
		final N5Reader n5Reader = URITools.instantiateN5Reader(StorageFormat.N5, coefficientsUri);

		final AtomicBoolean enableIntensityCorrection = new AtomicBoolean(false);

		final Bdv bdv = BdvFunctions.show();
		for (final SourceAndConverter<?> source : sources) {

			final int setupId = getSetupId(source.getSpimSource());
			final IntFunction<Coefficients> timepointToCoefficients = timepointIndex -> {
				final int timepointId = seq.getTimePoints().getTimePointsOrdered().get(timepointIndex).getId();
				final String path = IntensityCorrection.getCoefficientsDatasetPath("", "coefficients", setupId, timepointId);
				System.out.println("path = " + path);
				return CoefficientsIO.load(n5Reader, path);
			};

			final SourceAndConverter<?> intensityCorrrectedSource = wrapWithIntensityCorrectedSource(source, timepointToCoefficients, enableIntensityCorrection::get);
			BdvFunctions.show(intensityCorrrectedSource, numTimepoints, Bdv.options().addTo(bdv));
		}

		final ViewerPanel viewerPanel = bdv.getBdvHandle().getViewerPanel();
		InitializeViewerState.initBrightness(0.001, 0.999, viewerPanel.state().snapshot(), bdv.getBdvHandle().getConverterSetups());

		Actions actions = new Actions(viewerPanel.getInputTriggerConfig());
		actions.install(bdv.getBdvHandle().getKeybindings(), "intensity-correction");
		actions.runnableAction( () -> {
			final boolean b = !enableIntensityCorrection.get();
			enableIntensityCorrection.set(b);
			viewerPanel.showMessage("Intensity Correction " + (b ? "enabled" : "disabled"));
			viewerPanel.requestRepaint();
		}, "toggle intensity correction", "C" );

	}

	public static <T, V extends Volatile<T>> SourceAndConverter<T> wrapWithIntensityCorrectedSource(
			final SourceAndConverter<T> soc,
			final IntFunction<Coefficients> timepointToCoefficients,
			final BooleanSupplier enableIntensityCorrection
	) {
		return new SourceAndConverter<>(
				new IntensityCorrectedSource(soc.getSpimSource(), timepointToCoefficients, enableIntensityCorrection),
				soc.getConverter());
//		if (soc.asVolatile() == null)
//			return new SourceAndConverter<>(new IntensityCorrectedSource<>(soc.getSpimSource()), soc.getConverter());
//
//		@SuppressWarnings("unchecked")
//		final SourceAndConverter<V> vsoc = (SourceAndConverter<V>) soc.asVolatile();
//		final TransformedSource<T> ts = new TransformedSource<>(soc.getSpimSource());
//		final TransformedSource<V> vts = new TransformedSource<>(vsoc.getSpimSource(), ts);
//		return new SourceAndConverter<>(ts, soc.getConverter(), new SourceAndConverter<>(vts, vsoc.getConverter()));
	}


	private static int getSetupId(Source<?> source) {
		if (source instanceof TransformedSource)
			return getSetupId(((TransformedSource<?>) source).getWrappedSource());
		else if (source instanceof AbstractSpimSource)
			return ((AbstractSpimSource<?>) source).getSetupId();
		else
			throw new IllegalArgumentException();
	}



	static class IntensityCorrectedSource<T extends NativeType<T> & NumericType<T>> implements Source<T>, MipmapOrdering {

		private final Source< T > source;

		/**
		 * This is either the {@link #source} itself, if it implements
		 * {@link MipmapOrdering}, or a {@link DefaultMipmapOrdering}.
		 */
		private final MipmapOrdering sourceMipmapOrdering;

		/**
		 * Maps timepoint index to coefficients
		 */
		private final IntFunction<Coefficients> timepointToCoefficients;

		private final BooleanSupplier enableIntensityCorrection;

		private int currentTimePointIndex = -1;

		private boolean currentTimePointIsPresent;

		private final RandomAccessibleInterval<T>[] currentImgs;

		/**
		 * Instantiates a new {@link TransformedSource} wrapping the specified
		 * source with the identity transform.
		 *
		 * @param source
		 *            the source to wrap.
		 */
		IntensityCorrectedSource(
				final Source<T> source,
				final IntFunction<Coefficients> timepointToCoefficients,
				final BooleanSupplier enableIntensityCorrection
		) {
			this.source = source;
			this.sourceMipmapOrdering = source instanceof MipmapOrdering ?
					( MipmapOrdering ) source : new DefaultMipmapOrdering( source );
			this.timepointToCoefficients = timepointToCoefficients;
			this.currentImgs = new RandomAccessibleInterval[source.getNumMipmapLevels()];
			this.enableIntensityCorrection = enableIntensityCorrection;
		}

		private void loadTimepoint( final int timepointIndex )
		{
			currentTimePointIndex = timepointIndex;
			currentTimePointIsPresent = source.isPresent(timepointIndex);
			if (currentTimePointIsPresent) {
				final Coefficients coefficients = timepointToCoefficients.apply(timepointIndex);
				for (int level = 0; level < currentImgs.length; ++level) {
					final RandomAccessibleInterval<T> image = source.getSource(timepointIndex, level);
					final RandomAccessibleInterval<T> corrected = BlockSupplier.of(image)
							.andThen(FastLinearIntensityMap.linearIntensityMap(coefficients, image))
							.toCellImg(image.dimensionsAsLongArray(), 64);
					currentImgs[level] = corrected;
				}
			} else {
				Arrays.setAll(currentImgs, null);
			}
		}

		@Override
		public synchronized RandomAccessibleInterval<T> getSource(final int t, final int level) {
			if (enableIntensityCorrection.getAsBoolean()) {
				if (t != currentTimePointIndex)
					loadTimepoint(t);
				return currentImgs[level];
			} else {
				return source.getSource(t, level);
			}
		}

		@Override
		public synchronized RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method) {
			if (enableIntensityCorrection.getAsBoolean()) {
				if (t != currentTimePointIndex)
					loadTimepoint(t);
				if (!currentTimePointIsPresent)
					return null;
				return getSource(t, level)
						.view()
						.extend(zero())
						.interpolate(method == NEARESTNEIGHBOR ? nearestNeighbor() : clampingNLinear());
			} else {
				return source.getInterpolatedSource(t, level, method);
			}
		}

		@Override
		public synchronized MipmapHints getMipmapHints(final AffineTransform3D screenTransform, final int timepoint, final int previousTimepoint) {
			return sourceMipmapOrdering.getMipmapHints(screenTransform, timepoint, previousTimepoint);
		}

		@Override
		public boolean isPresent(final int t) {
			return source.isPresent(t);
		}

		@Override
		public boolean doBoundingBoxCulling() {
			return source.doBoundingBoxCulling();
		}

		@Override
		public void getSourceTransform(final int t, final int level, final AffineTransform3D transform) {
			source.getSourceTransform(t, level, transform);
		}

		@Override
		public T getType() {
			return source.getType();
		}

		@Override
		public String getName() {
			return source.getName();
		}

		@Override
		public VoxelDimensions getVoxelDimensions() {
			return source.getVoxelDimensions();
		}

		@Override
		public int getNumMipmapLevels() {
			return source.getNumMipmapLevels();
		}
	}
}
