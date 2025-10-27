/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2025 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.fusion.intensity;

import bdv.AbstractSpimSource;
import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.ui.UIUtils;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.viewer.render.AlphaWeightedAccumulateProjectorARGB;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.scijava.ui.behaviour.util.Actions;
import util.URITools;

public class IntensityVisualizationPlayground {


	public static void main(String[] args) throws URISyntaxException, SpimDataException {
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		UIUtils.installFlatLafInfos();

		final URI datasetUri = new URI("file:/nrs/tavakoli/data_internal/s12c/samples_for_stitching/20250902_mouse_hipp_3_channels/dataset_fix_ids_bg.xml");
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		final SpimData2 spimData = io.load(datasetUri);

		final AbstractSequenceDescription<?, ?, ?> seq = spimData.getSequenceDescription();
		final int numTimepoints = seq.getTimePoints().size();
		final ArrayList<SourceAndConverter<?>> allsources = new ArrayList<>();

		BigDataViewer.initSetups(spimData, new ArrayList<>(), allsources);

		System.out.println( "init done.");

		// filter for channel 0
		final List<SourceAndConverter<?>> sources = allsources.stream().filter( source -> getSetupId(source.getSpimSource()) <= 559 ).collect( Collectors.toList() );

		// filter for channel 1
		//final List<SourceAndConverter<?>> sources = allsources.stream().filter( source -> getSetupId(source.getSpimSource()) >= 560 && getSetupId(source.getSpimSource()) < 1120 ).collect( Collectors.toList() );

		// filter for channel 2
		//final List<SourceAndConverter<?>> sources = allsources.stream().filter( source -> getSetupId(source.getSpimSource()) >= 1120 ).collect( Collectors.toList() );

		// TODO: if a setup??/timepoint??/ does not exist, what does that mean?
		// TODO: why are they missing in the first place? seems like the match process was simply interrupted? double-check threshold-8,8,8
		final URI coefficientsUri = new File("/nrs/tavakoli/data_internal/s12c/samples_for_stitching/20250902_mouse_hipp_3_channels/intensity_spark_histogram_bg.n5").toURI();
		final N5Reader n5Reader = URITools.instantiateN5Reader(StorageFormat.N5, coefficientsUri);

		final AtomicBoolean enableIntensityCorrection = new AtomicBoolean(false);

		final SharedQueue sharedQueue = new SharedQueue(128);
		final Bdv bdv = BdvFunctions.show(Bdv.options().accumulateProjectorFactory(AlphaWeightedAccumulateProjectorARGB.factory));
		for (final SourceAndConverter<?> source : sources) {

			final int setupId = getSetupId(source.getSpimSource());
			final IntFunction<Coefficients> timepointToCoefficients = timepointIndex -> {
				final int timepointId = seq.getTimePoints().getTimePointsOrdered().get(timepointIndex).getId();
				final String path = IntensityCorrection.getCoefficientsDatasetPath("", "intensity", setupId, timepointId);
				System.out.println("path = " + path);
				return CoefficientsIO.load(n5Reader, path);
			};

			final SourceAndConverter<?> intensityCorrectedSource = VisualizeIntensityCorrection.wrapWithIntensityCorrectedSource(
					source,
					sharedQueue,
					timepointToCoefficients,
					enableIntensityCorrection::get);
			BdvFunctions.show(intensityCorrectedSource, numTimepoints, Bdv.options().addTo(bdv));
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

	private static int getSetupId(Source<?> source) {
		if (source instanceof TransformedSource)
			return getSetupId(((TransformedSource<?>) source).getWrappedSource());
		else if (source instanceof AbstractSpimSource)
			return ((AbstractSpimSource<?>) source).getSetupId();
		else
			throw new IllegalArgumentException();
	}

}
