package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.statistics;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import mpicbg.spim.io.IOFunctions;

import org.jfree.chart.ChartPanel;

public class SelectTimepointEntry extends AbstractAction 
{
	private static final long serialVersionUID = 1L;
	
	final ArrayList< RegistrationStatistics > data;
	ChartPanel chartPanel;
	
	public SelectTimepointEntry( final String title, final ArrayList< RegistrationStatistics > data )
	{
		super( title );
		this.data = data;
	}

	/*
	 * This method is called by the GraphFrame upon initialization
	 * 
	 * @param chartPanel
	 */
	public void setChartPanel( final ChartPanel chartPanel ) { this.chartPanel = chartPanel; }
	
	@Override
	public void actionPerformed( final ActionEvent e ) 
	{
		if ( chartPanel != null )
		{
			// this might fail horribly, but at the moment it is the only solution
			// as right clicks in the chart are not reported to the mouse-listener
			// if they happen above the line drawings
			try
			{
				final JMenuItem item = (JMenuItem)e.getSource(); 
				final JPopupMenu m = (JPopupMenu)item.getParent();
	
				// location of the top left pixel of the chartpanel in screen coordinates
				final Point p = chartPanel.getLocationOnScreen();
	
				// we parse the position of the JPopupMenu on the screen (AAARGH!!!)
				final String output = m.toString();
	
				final String x = output.substring( output.indexOf( "desiredLocationX" ) );
				final String y = output.substring( output.indexOf( "desiredLocationY" ) );
	
				System.out.println( "chart: " +p );

				System.out.println( "popup: " + x + ", " + y );

				// and from that we get the relative coordinate in the chartpanel 
				p.x = Integer.parseInt( x.substring( x.indexOf( "=" )+1, x.indexOf( "," ) ) ) - p.x;
				p.y = Integer.parseInt( y.substring( y.indexOf( "=" )+1, y.indexOf( "," ) ) ) - p.y;
				
				// now we transform it into the correct timelapse scale
				final int tp = MouseListenerTimelapse.getChartXLocation( p, chartPanel );
				
				// now find the correct image
				for ( final RegistrationStatistics stat : data )
					if ( stat.getTimePoint() == tp )
					{
						IOFunctions.println( "Selected timepoint: " + tp );
						break;
					}
			}
			catch ( Exception ex ) {}
		}
	}

}
