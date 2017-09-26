package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class SimpleInfoBox
{
	final JFrame frame;

	public SimpleInfoBox( final String title, final String text )
	{
		frame = new JFrame( title );

		final JTextArea textarea = new JTextArea( text );

		final JPanel panel = new JPanel();
		panel.add( textarea, BorderLayout.CENTER );
		final JScrollPane pane = new JScrollPane( panel );
		frame.add( pane, BorderLayout.CENTER );

		frame.pack();

		final Dimension d = pane.getSize();
		d.setSize( d.width + 20, d.height + 10 );
		pane.setSize( d );
		pane.setPreferredSize( d );
		frame.setPreferredSize( d );

		frame.pack();
		frame.setVisible( true );
	}
}
