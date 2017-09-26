package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;

import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.fusion.FusionTools;

import ij.gui.GenericDialog;

public class ManageFusionDialogListeners
{
	final GenericDialog gd;
	final TextField downsampleField;
	final Choice boundingBoxChoice, pixelTypeChoice, cachingChoice, splitChoice;
	final Checkbox contentbasedCheckbox, anisoCheckbox;
	final Label label1;
	final Label label2;
	final FusionGUI fusion;

	double anisoF;

	public ManageFusionDialogListeners(
			final GenericDialog gd,
			final Choice boundingBoxChoice,
			final TextField downsampleField,
			final Choice pixelTypeChoice,
			final Choice cachingChoice,
			final Checkbox contentbasedCheckbox,
			final Checkbox anisoCheckbox,
			final Choice splitChoice,
			final Label label1,
			final Label label2,
			final FusionGUI fusion )
	{
		this.gd = gd;
		this.boundingBoxChoice = boundingBoxChoice;
		this.downsampleField = downsampleField;
		this.pixelTypeChoice = pixelTypeChoice;
		this.cachingChoice = cachingChoice;
		this.contentbasedCheckbox = contentbasedCheckbox;
		this.anisoCheckbox = anisoCheckbox;
		this.splitChoice = splitChoice;
		this.label1 = label1;
		this.label2 = label2;
		this.fusion = fusion;

		this.boundingBoxChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.downsampleField.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update(); } });

		this.pixelTypeChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.cachingChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.splitChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.contentbasedCheckbox.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		if ( this.anisoCheckbox != null )
		{
			System.out.println( "Add" );
			this.anisoF = fusion.getAnisotropyFactor();
			this.anisoCheckbox.addItemListener( new ItemListener() { @Override
				public void itemStateChanged(ItemEvent e) { update(); } });
		}
		else
		{
			System.out.println( "null" );
		}
}
	
	public void update()
	{
		fusion.boundingBox = boundingBoxChoice.getSelectedIndex();
		fusion.downsampling = Integer.parseInt( downsampleField.getText() );
		fusion.pixelType = pixelTypeChoice.getSelectedIndex();
		fusion.cacheType = cachingChoice.getSelectedIndex();
		fusion.useContentBased = contentbasedCheckbox.getState();
		fusion.splittingType = splitChoice.getSelectedIndex();
		if ( anisoCheckbox != null )
		{
			fusion.preserveAnisotropy = anisoCheckbox.getState();
			
			if ( fusion.preserveAnisotropy )
				this.anisoF = fusion.getAnisotropyFactor();
			else
				this.anisoF = 1.0;
		}
		else
		{
			this.anisoF = 1.0;
			fusion.preserveAnisotropy = false;
		}

		final BoundingBox bb = fusion.allBoxes.get( fusion.boundingBox );
		final long numPixels = Math.round( FusionTools.numPixels( bb, fusion.downsampling ) / anisoF );

		final int bytePerPixel;
		if ( fusion.pixelType == 1 )
			bytePerPixel = 2;
		else
			bytePerPixel = 4;

		final long megabytes = (numPixels * bytePerPixel) / (1024*1024);

		label1.setText( "Fused image: " + megabytes + " MB, required total memory ~" + totalRAM( megabytes, bytePerPixel ) +  " MB" );
		label1.setForeground( GUIHelper.good );

		final int[] min = bb.getMin().clone();
		final int[] max = bb.getMax().clone();

		if ( fusion.preserveAnisotropy )
		{
			min[ 2 ] = (int)Math.round( Math.floor( min[ 2 ] / anisoF ) );
			max[ 2 ] = (int)Math.round( Math.ceil( max[ 2 ] / anisoF ) );
		}

		label2.setText( "Dimensions: " + 
				Math.round( (max[ 0 ] - min[ 0 ] + 1)/fusion.downsampling ) + " x " + 
				Math.round( (max[ 1 ] - min[ 1 ] + 1)/fusion.downsampling ) + " x " + 
				Math.round( (max[ 2 ] - min[ 2 ] + 1)/(fusion.downsampling ) ) + " pixels @ " + FusionGUI.pixelTypes[ fusion.pixelType ] );
	}

	public long totalRAM( long fusedSizeMB, final int bytePerPixel )
	{
		// do we need to load the image data fully?
		long inputImagesMB = 0;

		long maxNumPixelsInput = FusionGUI.maxNumInputPixelsPerInputGroup( fusion.getSpimData(), fusion.getViews(), fusion.getSplittingType() );

		// assume he have to load 50% higher resolved data
		double inputDownSampling = fusion.isMultiResolution() ? fusion.downsampling / 1.5 : 1.0;

		final int inputBytePerPixel = FusionGUI.inputBytePerPixel( fusion.views.get( 0 ), fusion.spimData );

		if ( fusion.isImgLoaderVirtual() )
		{
			// either 50% of the RAM or 5% of the downsampled input
			inputImagesMB = Math.min(
					Runtime.getRuntime().maxMemory() / ( 1024*1024*2 ),
					( ( ( maxNumPixelsInput / Math.round( inputDownSampling * 1024*1024 ) ) * inputBytePerPixel ) / 20 ) );
		}
		else
		{
			inputImagesMB = Math.round( maxNumPixelsInput / ( inputDownSampling * 1024*1024 ) ) * inputBytePerPixel;
		}

		long processingMB = 0;

		if ( fusion.useContentBased )
		{
			if ( fusion.isMultiResolution() )
				processingMB = ( maxNumPixelsInput / ( 1024*1024 ) ) * 4;
			else
				processingMB = ( maxNumPixelsInput / Math.round( inputDownSampling * 1024*1024 ) ) * 4;
		}

		if ( fusion.cacheType == 0 ) // Virtual
			fusedSizeMB /= Math.max( 1, Math.round( Math.pow( fusedSizeMB, 0.3 ) ) );
		else if ( fusion.cacheType == 1 ) // Cached
			fusedSizeMB = 2 * Math.round( fusedSizeMB / Math.max( 1, Math.pow( fusedSizeMB, 0.3 ) ) );

		return inputImagesMB + processingMB + fusedSizeMB;
	}
}
