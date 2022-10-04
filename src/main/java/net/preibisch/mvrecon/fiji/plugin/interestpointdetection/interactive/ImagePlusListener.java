package net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive;

import fiji.tool.SliceListener;
import ij.ImagePlus;
import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive.InteractiveRadialSymmetry.ValueChange;

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
				try {
					Thread.sleep( 10 );
				} catch (InterruptedException e) {}
			}
			parent.updatePreview(ValueChange.SLICE);
		}
	}
}
