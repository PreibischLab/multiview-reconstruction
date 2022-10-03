package net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive;

import fiji.tool.SliceListener;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive.InteractiveRadialSymmetry.ValueChange;
import ij.ImagePlus;

public class ImagePlusListener implements SliceListener
{
	final InteractiveRadialSymmetry parent;

	public ImagePlusListener( final InteractiveRadialSymmetry parent )
	{
		this.parent = parent;
	}

	@Override
	public void sliceChanged(ImagePlus arg0) {
		if (parent.isStarted) {
			// System.out.println("Slice changed!");
			while (parent.isComputing) {
				SimpleMultiThreading.threadWait(10);
			}
			parent.updatePreview(ValueChange.SLICE);
		}
	}
}
