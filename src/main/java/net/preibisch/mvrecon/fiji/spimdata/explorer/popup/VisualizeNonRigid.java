package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.swing.JMenuItem;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI;
import net.preibisch.mvrecon.fiji.plugin.fusion.NonRigidParametersGUI;
import net.preibisch.mvrecon.fiji.plugin.interactive.MultiResolutionSource;
import net.preibisch.mvrecon.fiji.plugin.interactive.MultiResolutionTools;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.NonRigidTools;

public class VisualizeNonRigid extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = -4858927229313796971L;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	final private static String[] displayOptions = new String[] {
					"Overlay all views affine vs. non-rigid",
					"Overlay all views affine vs. previous affine",
					"Overlay selected views using non-rigid",
					"Overlay selected views using affine" };

	public static int defaultDisplay = 0;

	public VisualizeNonRigid()
	{
		super( "Preview Non-Rigid Transformation ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );
					final SpimData2 spimData = (SpimData2)panel.getSpimData();

					// filter not present ViewIds
					final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

					final GenericDialog gd = new GenericDialog( "Preview Affine/Non-Rigid" );

					final List< BoundingBox > allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, viewIds, true );
					final String[] choices = FusionGUI.getBoundingBoxChoices( allBoxes );

					gd.addChoice( "Bounding_Box", choices, choices[ FusionGUI.defaultBB ] );
					gd.addChoice( "Display options", displayOptions, displayOptions[ defaultDisplay ] );
					gd.addMessage( "Please note, this will open a new BDV window", GUIHelper.mediumstatusfont, Color.DARK_GRAY );
					gd.addMessage( "You can check how the affine model changed or how the non-rigid compares to affine.", GUIHelper.smallStatusFont, Color.DARK_GRAY );

					gd.showDialog();
					if ( gd.wasCanceled() )
						return;

					final int boundingBoxIndex = FusionGUI.defaultBB = gd.getNextChoiceIndex();
					final int display = defaultDisplay = gd.getNextChoiceIndex();

					NonRigidParametersGUI params = null;
					if ( display != 3 )
					{
						params = new NonRigidParametersGUI( spimData, viewIds );
						if ( !params.query() || !params.isActive() )
							return;
					}

					final BoundingBox boundingBox = allBoxes.get( boundingBoxIndex );

					final int minDS = 1;
					final int maxDS = 16;
					final int dsInc = 2;

					if ( display == 0 )
					{
						// Overlay all views affine vs. non-rigid

						// affine
						final ArrayList< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > multiResAffine =
								MultiResolutionTools.createMultiResolutionAffine( spimData, viewIds, boundingBox, minDS, maxDS, dsInc );

						// non-rigid
						final List< ViewId > viewsToFuse = new ArrayList< ViewId >(); // fuse
						viewsToFuse.addAll( viewIds );

						final List< ViewId > viewsToUse =
								NonRigidTools.assembleViewsToUse( spimData, viewIds, params.nonRigidAcrossTime() );

						final int interpolation = 1;
						final boolean useBlending = false;
						final boolean useContentBased = false;

						final ExecutorService service = DeconViews.createExecutorService();

						final ArrayList< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > multiResNonRigid =
								MultiResolutionTools.createMultiResolutionNonRigid(
										spimData,
										viewsToFuse,
										viewsToUse,
										params.getLabels(),
										useBlending,
										useContentBased,
										params.showDistanceMap(),
										params.getControlPointDistance(),
										params.getAlpha(),
										interpolation,
										boundingBox,
										null,
										service,
										minDS,
										maxDS,
										dsInc );

						BdvOptions options = Bdv.options().numSourceGroups( 2 );
						BdvStackSource< ? > affine = BdvFunctions.show( new MultiResolutionSource( MultiResolutionTools.createVolatileRAIs( multiResAffine ), "affine" ), options );
						affine.setDisplayRange( 0, 200 );
						affine.setColor( new ARGBType( ARGBType.rgba( 255, 0, 255, 0 ) ) );

						options.addTo( affine );
						BdvStackSource< ? > nr = BdvFunctions.show( new MultiResolutionSource( MultiResolutionTools.createVolatileRAIs( multiResNonRigid ), "nonrigid" ), options );
						nr.setDisplayRange( 0, 200 );
						nr.setColor( new ARGBType( ARGBType.rgba( 0, 255, 0, 0 ) ) );
						MultiResolutionTools.updateBDV( nr );
					}
					else
					{
						
					}
				}
			} ).start();
		}
	}}
