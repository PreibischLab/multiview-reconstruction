package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.preibisch.mvrecon.fiji.plugin.PSF_Assign;
import net.preibisch.mvrecon.fiji.plugin.PSF_Average;
import net.preibisch.mvrecon.fiji.plugin.PSF_Extract;
import net.preibisch.mvrecon.fiji.plugin.PSF_View;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class PointSpreadFunctionsPopup extends JMenu implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 1L;

	ExplorerWindow< ?, ? > panel = null;

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;

		return this;
	}

	public PointSpreadFunctionsPopup()
	{
		super( "Point Spead Functions" );

		final JMenuItem extract = new JMenuItem( "Extract ..." );
		final JMenu assign = new JMenu( "Assign" );
		final JMenu average = new JMenu( "Average" );
		final JMenu display = new JMenu( "Display" );

		// extraction
		extract.addActionListener( new ExtractPSFs() );
		this.add( extract );

		// assignment
		final JMenu assignExistingPSF = new JMenu( "PSF from view" );
		final JMenuItem assignAdvanced = new JMenuItem( "Advanced ..." );

		assignExistingPSF.addMenuListener( new MenuListener()
		{
			@Override
			public void menuSelected( MenuEvent e )
			{
				assignExistingPSF.removeAll();

				final SpimData2 spimData = (SpimData2)panel.getSpimData();
				final ArrayList< ViewId > psfs = PSF_Assign.viewsWithUniquePSFs( spimData.getPointSpreadFunctions() );
				final String[] psfTitles = PSF_Assign.assemblePSFs( psfs, spimData.getPointSpreadFunctions().getPointSpreadFunctions() );

				if ( psfTitles.length == 0 )
				{
					JMenuItem item = new JMenuItem( "No PSFs found" );
					item.setForeground( Color.GRAY );
					assignExistingPSF.add( item );
				}
				else
				{
					for ( int i = 0; i < psfTitles.length; ++i )
					{
						JMenuItem item = new JMenuItem( psfTitles[ i ] );
						item.addActionListener( new AssignPSF( psfs.get( i ) ) );
						assignExistingPSF.add( item );
					}
				}
			}

			@Override
			public void menuDeselected( MenuEvent e ) {}

			@Override
			public void menuCanceled( MenuEvent e ) {}
		} );

		assignAdvanced.addActionListener( new AssignAdvanced() );
		assign.add( assignExistingPSF );
		assign.add( assignAdvanced );

		this.add( assign );

		// average
		final JMenuItem averageDisplay = new JMenuItem( PSF_Average.averagingChoices[ 0 ] ); //"Display"
		final JMenuItem averageAssign = new JMenuItem( PSF_Average.averagingChoices[ 1 ] ); //Assign to input views"
		final JMenuItem averageAssignAndDisplay = new JMenuItem( PSF_Average.averagingChoices[ 2 ] ); //Display & assign to input views"

		averageDisplay.addActionListener( new AveragePSF( 0 ) );
		averageAssign.addActionListener( new AveragePSF( 1 ) );
		averageAssignAndDisplay.addActionListener( new AveragePSF( 2 ) );

		average.add( averageDisplay );
		average.add( averageAssign );
		average.add( averageAssignAndDisplay );

		this.add( average );

		// display
		final JMenuItem displayRaw = new JMenuItem( "Raw PSF for each view" ); //"Averaged PSF",
		final JMenuItem displayAverage = new JMenuItem( PSF_View.displayChoices[ 0 ] ); //"Averaged PSF",
		final JMenuItem displayTransformedAverage = new JMenuItem( PSF_View.displayChoices[ 1 ] ); //"Averaged transformed PSF",
		final JMenuItem displayMaxAverage = new JMenuItem( PSF_View.displayChoices[ 2 ] ); //"Maximum Projection of averaged PSF",
		final JMenuItem displayMaxTransformedAverage = new JMenuItem( PSF_View.displayChoices[ 3 ] ); //"Maximum Projection of averaged transformed PSF" };

		displayRaw.addActionListener( new DisplayPSF( -1 ) );
		displayAverage.addActionListener( new DisplayPSF( 0 ) );
		displayTransformedAverage.addActionListener( new DisplayPSF( 1 ) );
		displayMaxAverage.addActionListener( new DisplayPSF( 2 ) );
		displayMaxTransformedAverage.addActionListener( new DisplayPSF( 3 ) );

		display.add( displayRaw );
		display.add( displayAverage );
		display.add( displayTransformedAverage );
		display.add( displayMaxAverage );
		display.add( displayMaxTransformedAverage );

		this.add( display );
	}

	private class DisplayPSF implements ActionListener
	{
		final int choice;

		public DisplayPSF( final int choice ) { this.choice = choice; }

		@Override
		public void actionPerformed( ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final SpimData2 spimData = (SpimData2)panel.getSpimData();

					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( spimData, views );

					if ( choice == -1 )
					{
						for ( final ViewId v : views )
						{
							DisplayImage.getImagePlusInstance(
								spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( v ).getPSFCopy(),
								false,
								"PSF " + Group.pvid( v ),
								Double.NaN,
								Double.NaN ).show();;
						}
					}
					else
					{
						PSF_View.display( spimData, views, choice );
					}
				}
			} ).start();
		}
	}

	private class AveragePSF implements ActionListener
	{
		final int choice;

		public AveragePSF( final int choice ) { this.choice = choice; }

		@Override
		public void actionPerformed( ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final SpimData2 spimData = (SpimData2)panel.getSpimData();

					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( spimData, views );

					if ( PSF_Average.average( spimData, views, choice ) )
						panel.updateContent(); // update panel
				}
			} ).start();
		}
	}

	private class AssignPSF implements ActionListener
	{
		final ViewId sourceViewId;

		public AssignPSF( final ViewId viewId ) { this.sourceViewId = viewId; }

		@Override
		public void actionPerformed( ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final SpimData2 spimData = (SpimData2)panel.getSpimData();

					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( panel.getSpimData(), views );

					final String file = spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( sourceViewId ).getFile();

					for ( final ViewId viewId : views )
					{
						IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assigning '" + file + "' to " + Group.pvid( viewId ) );
						spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData.getBasePath(), file ) );
					}

					panel.updateContent(); // update panel
				}
			} ).start();
		}
	}

	private class ExtractPSFs implements ActionListener
	{
		@Override
		public void actionPerformed( ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( PSF_Extract.extract( (SpimData2)panel.getSpimData(), ApplyTransformationPopup.getSelectedViews( panel ) ) )
						panel.updateContent(); // update panel
				}
			} ).start();
		}
	}

	private class AssignAdvanced implements ActionListener
	{
		@Override
		public void actionPerformed( ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( PSF_Assign.assign( (SpimData2)panel.getSpimData(), ApplyTransformationPopup.getSelectedViews( panel ) ) )
						panel.updateContent(); // update panel
				}
			} ).start();
		}
	}

}
