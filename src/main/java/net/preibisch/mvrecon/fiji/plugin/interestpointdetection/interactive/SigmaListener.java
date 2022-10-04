package net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive;

import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive.InteractiveRadialSymmetry.ValueChange;

public class SigmaListener implements AdjustmentListener {
	final InteractiveRadialSymmetry parent;
	final Label label;
	final float min, max;
	final int scrollbarSize;

	final Scrollbar sigmaScrollbar1;

	public SigmaListener(
			final InteractiveRadialSymmetry parent,
			final Label label, final float min, final float max,
			final int scrollbarSize,
			final Scrollbar sigmaScrollbar1) {
		this.parent = parent;
		this.label = label;
		this.min = min;
		this.max = max;
		this.scrollbarSize = scrollbarSize;

		this.sigmaScrollbar1 = sigmaScrollbar1;
	}

	@Override
	public void adjustmentValueChanged(final AdjustmentEvent event) {
		parent.params.sigma = HelperFunctions.computeValueFromScrollbarPosition(event.getValue(), min, max, scrollbarSize);
		label.setText("Sigma 1 = " + String.format(java.util.Locale.US, "%.3f", parent.params.sigma));

		// Real time change of the radius
		// if ( !event.getValueIsAdjusting() )
		{
			while (parent.isComputing) {
				try {
					Thread.sleep( 10 );
				} catch (InterruptedException e) {}
			}
			parent.updatePreview(ValueChange.SIGMA);
		}
	}
}
