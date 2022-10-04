package net.preibisch.mvrecon.fiji.plugin.interestpointdetection.interactive;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class FrameListener extends WindowAdapter {
	final InteractiveDoG parent;

	public FrameListener(
			final InteractiveDoG parent )
	{
		super();
		this.parent = parent;
	}

	@Override
	public void windowClosing(WindowEvent e) {
		parent.dispose();
	}
}