package util;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.IntStream;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import fiji.tool.SliceListener;
import fiji.tool.SliceObserver;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.plugin.Animator;
import ij.plugin.PlugIn;
import ij.plugin.Zoom;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import net.imglib2.type.numeric.ARGBType;

@Plugin(type = Command.class, menuPath = "Plugins>Funke lab>Annotator ...")
public class MLTool implements PlugIn
{
	// Done: default Mask: 0.5, red
	// Done: on start ask for root directory
	// Done: zoom to 300%
	// Done: play button (with FPS) > turn into 4D image
	// Done: support images: 
	// directory a,b,m: increasing ID, no leading zeros
	// Done: save in the parent directory
	// Done: first iteration a single text file
	public static final String notes = "notes.txt";

	final ForkJoinPool myPool = new ForkJoinPool( Runtime.getRuntime().availableProcessors() );

	volatile ForkJoinTask<?> task = null;

	final String labelDialog = "Annotation for image ";
	final String impDialog = "Image ";

	JDialog dialog;
	JSlider sliderImg, sliderMask;
	JButton text1, text2, text3, text4;
	JButton back, forward, save, quit;
	JTextArea textfield;

	ByteProcessor ip1, ip2, mask;
	ImagePlus mainImp;
	//ColorProcessor main;
	HashMap< Integer, ColorProcessor > main;
	ImageStack stack;

	Color color = setColor( Color.orange );
	float r, g, b;

	SliceObserver sliceObserver;
	String dir;
	ByteProcessor[] imgsA, imgsB, imgsM;

	public void setup( final String dir )
	{
		this.dir = dir;

		final File dirA = new File( dir, "A" );
		final File dirB = new File( dir, "B" );
		final File dirM = new File( dir, "M" );

		final List<String> filesA = Arrays.asList( dirA.list( (d,n) -> n.toLowerCase().endsWith( ".png" ) ) );
		final List<String> filesB = Arrays.asList( dirB.list( (d,n) -> n.toLowerCase().endsWith( ".png" ) ) );
		final List<String> filesM = Arrays.asList( dirM.list( (d,n) -> n.toLowerCase().endsWith( ".png" ) ) );

		if ( filesA == null || filesA.size() == 0 )
		{
			IJ.log( "No PNG's found in " + dirA );
			throw new RuntimeException( "No PNG's found in " + dirA );
		}

		if ( filesB == null || filesB.size() == 0 )
		{
			IJ.log( "No PNG's found in " + dirB );
			throw new RuntimeException( "No PNG's found in " + dirB );
		}

		if ( filesM == null || filesM.size() == 0 )
		{
			IJ.log( "No PNG's found in " + dirM );
			throw new RuntimeException( "No PNG's found in " + dirM );
		}

		if ( filesA.size() != filesB.size() || filesA.size() != filesM.size() )
		{
			IJ.log( "Amount of files is not equal..." );
			throw new RuntimeException( "Amount of files is not equal..." );
		}

		IJ.log( "found: " + filesA.size() + " pngs in A, " + filesB.size() + " pngs in B, "+ filesM.size() + " pngs in M; loading all ...");

		final long time = System.currentTimeMillis();

		final int numFiles = filesA.size();

		imgsA = new ByteProcessor[ numFiles ];
		imgsB = new ByteProcessor[ numFiles ];
		imgsM = new ByteProcessor[ numFiles ];

		IntStream.range( 0, numFiles ).parallel().forEach( i ->
		{
			final ImagePlus impA = new ImagePlus( new File( dirA.getAbsolutePath(), i + ".png" ).getAbsolutePath() );
			final ImagePlus impB = new ImagePlus( new File( dirB.getAbsolutePath(), i + ".png" ).getAbsolutePath() );
			final ImagePlus mask = new ImagePlus( new File( dirM.getAbsolutePath(), i + ".png" ).getAbsolutePath() );

			imgsA[ i ] = new ByteProcessor( impA.getWidth() + 32, impA.getHeight() );
			imgsB[ i ] = new ByteProcessor( impA.getWidth() + 32, impA.getHeight() );
			imgsM[ i ] = new ByteProcessor( impA.getWidth() + 32, impA.getHeight() );

			final ByteProcessor ipTmpA = (ByteProcessor)impA.getProcessor();
			final ByteProcessor ipTmpB = (ByteProcessor)impB.getProcessor();
			final ByteProcessor ipTmpM = (ByteProcessor)mask.getProcessor();

			for ( int y = 0; y < impA.getHeight(); ++y )
				for ( int x = 0; x < impA.getWidth(); ++x )
				{
					final int i1 = y * impA.getWidth() + x;
					final int i2 = y * imgsA[ i ].getWidth() + x;

					imgsA[ i ].set( i2, ipTmpA.get( i1 ) );
					imgsB[ i ].set( i2, ipTmpB.get( i1 ) );
					imgsM[ i ].set( i2, ipTmpM.get( i1 ) );
				}
		});

		IJ.log( "Done, took " + ( System.currentTimeMillis() - time ) + " ms." );

		setImages( imgsA[ 0 ], imgsB[ 0 ], imgsM[ 0 ] );
	}

	public void setImages( final ByteProcessor ip1, final ByteProcessor ip2, final ByteProcessor mask )
	{
		this.ip1 = ip1;
		this.ip2 = ip2;
		this.mask = mask;
	}

	public synchronized void interpolateMainImage()
	{
		if ( task != null )
		{
			task.cancel( true );
			task = null;
		}

		try
		{
			task = myPool.submit(() ->
				main.entrySet().parallelStream().parallel().forEach( entry ->
					interpolateMask(ip1, ip2, (float)entry.getKey() / 100f, mask, (float)sliderMask.getValue() / 100f, r,g,b , entry.getValue())
				));//.get();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		mainImp.updateAndDraw();
	}

	public Color setColor( final Color color )
	{
		if ( color == null )
			return null;

		this.color = color;

		this.r = 1.0f - color.getRed() / 255f;
		this.g = 1.0f - color.getGreen() / 255f;
		this.b = 1.0f - color.getBlue() / 255f;

		return color;
	}

	public static void interpolateMask( final ByteProcessor input1, final ByteProcessor input2, final float amount, final ByteProcessor mask, final float amountMask, final float amountR, final float amountG, final float amountB, final ColorProcessor target )
	{
		final int numPixels = target.getWidth() * target.getHeight();
		final float invAmount = 1.0f - amount;

		for ( int i = 0; i < numPixels; ++i )
		{
			final float v = input1.get( i ) * invAmount + input2.get( i ) * amount;
			final float maskedV = (mask.getf( i ) / 255.0f) * amountMask;
			final float tmp = v * maskedV;

			target.set( i, ARGBType.rgba(v - amountR*tmp, v- amountG*tmp, v- amountB*tmp, 255.0f) );
		}
	}

	public boolean load()
	{
		final File f = new File( dir, notes );

		if ( !f.exists() )
			return false;

		try
		{
			final BufferedReader inputFile = new BufferedReader(new FileReader( f ));

			String concatenated = "";
			String l = null;

			while ( ( l = inputFile.readLine() ) != null )
				concatenated += l + "\n";

			concatenated = concatenated.trim();

			textfield.setText( concatenated );

			inputFile.close();
		}
		catch (Exception e)
		{
			IJ.log( "Couldn't load file: '" + f + "': " + e);
			e.printStackTrace();
			return false;
		}

		IJ.log( "Successfully LOADED file: '" + f + "'." );

		return true;
	}

	public boolean save()
	{
		final String fn = new File( dir, notes ).getAbsolutePath();
		try
		{
			final PrintWriter outputFile = new PrintWriter(new FileWriter( fn ));
			outputFile.print( textfield.getText().trim() );
			outputFile.close();
		}
		catch (IOException e)
		{
			IJ.log( "Couldn't save file: '" + fn + "': " + e);
			e.printStackTrace();
			return false;
		}

		IJ.log( "Successfully SAVED file: '" + fn + "'." );

		return true;
	}

	public void showDialog(
			final int maxFrame,
			final double defaultMagnification,
			final int defaultMask,
			final Color defaultColor,
			final boolean loadExisting )
	{
		setColor( defaultColor );

		// create dialog
		dialog = new JDialog( (JFrame)null, labelDialog + "0", false);
		dialog.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 1;
		text1 = new JButton( "Image 0" );
		text1.setBorderPainted( false );
		dialog.add( text1, c );

		c.gridx = 1;
		c.gridy = 0;
		c.gridwidth = 2;
		sliderImg = new JSlider( 0, imgsA.length - 1 );
		sliderImg.setValue( 0 );
		sliderImg.addChangeListener( e -> {
			setImages( imgsA[ sliderImg.getValue() ], imgsB[ sliderImg.getValue() ], imgsM[ sliderImg.getValue() ] );
			interpolateMainImage();
			dialog.setTitle( labelDialog + sliderImg.getValue() );
			mainImp.setTitle( impDialog + sliderImg.getValue() );
		});
		dialog.add( sliderImg, c );

		c.gridx = 3;
		c.gridy = 0;
		c.gridwidth = 1;
		text2 = new JButton( "Image " + ( imgsA.length - 1 ) );
		text2.setBorderPainted( false );
		dialog.add( text2, c );

		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 1;
		text3 = new JButton( "Mask 0.0" );
		text3.setBorderPainted( false );
		text3.setForeground( color );
		text3.addActionListener( e ->
		{
			setColor( JColorChooser.showDialog( dialog, "Choose a color", color ) );
			text3.setForeground( color );
			text4.setForeground( color );
			interpolateMainImage();
		} );
		dialog.add( text3, c );

		c.gridx = 1;
		c.gridy = 1;
		c.gridwidth = 2;
		sliderMask = new JSlider( 0, 100 );
		sliderMask.setValue( defaultMask );
		sliderMask.addChangeListener( e -> interpolateMainImage() );
		dialog.add( sliderMask, c );

		c.gridx = 3;
		c.gridy = 1;
		c.gridwidth = 1;
		text4 = new JButton( "Mask 1.0" );
		text4.setBorderPainted( false );
		text4.setForeground( color );
		text4.addActionListener( e ->
		{
			setColor( JColorChooser.showDialog( dialog, "Choose a color", color ) );
			text3.setForeground( color );
			text4.setForeground( color );
			interpolateMainImage();
		} );
		dialog.add( text4, c );


		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 1;
		back = new JButton( "Prev. Img" );
		back.addActionListener( e -> sliderImg.setValue( sliderImg.getValue() - 1) ) ;
		dialog.add( back, c );

		c.gridx = 1;
		c.gridy = 2;
		c.gridwidth = 1;
		forward = new JButton( "Next Img" );
		forward.addActionListener( e -> sliderImg.setValue( sliderImg.getValue() + 1) ) ;
		dialog.add( forward, c );

		c.gridx = 2;
		c.gridy = 2;
		c.gridwidth = 1;
		save = new JButton( "Save" );
		save.addActionListener( e -> save() );
		dialog.add( save, c );

		c.gridx = 3;
		c.gridy = 2;
		c.gridwidth = 1;
		quit = new JButton( "Quit" );
		quit.addActionListener( e ->
		{
			final int choice = JOptionPane.showConfirmDialog( dialog,
					"Do you want to save before closing?",
					"Confirmation",
					JOptionPane.YES_NO_CANCEL_OPTION );
			
			if ( choice == JOptionPane.CANCEL_OPTION )
				return;
			else if ( choice == JOptionPane.YES_OPTION )
				if ( !save() )
					return;

			sliceObserver.unregister();
			mainImp.close();
			dialog.dispose();
		});
		dialog.add( quit, c );

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 4;
		c.gridheight = 2;
		c.ipady = 200;
		c.ipadx = 300;
		textfield = new JTextArea();
		dialog.add( new JScrollPane(textfield), c );

		// try loading an existing notes file
		if ( loadExisting )
			load();

		// show dialog
		dialog.pack();
		dialog.setVisible(true);

		// setup ImageJ window
		this.main = new HashMap<>();
		this.stack = new ImageStack();

		// create empty imagestack
		for ( int f = 0; f <= maxFrame; ++f )
		{
			final ColorProcessor cp = new ColorProcessor( imgsA[ 0 ].getWidth(), imgsA[ 0 ].getHeight());
			this.main.put( f, cp );
			this.stack.addSlice( cp );
		}

		// create image window
		this.mainImp = new ImagePlus( impDialog + "0", this.stack );

		// setup calibration for animation
		final Calibration cal = this.mainImp.getCalibration();
		cal.fps = 25;
		cal.loop = true;

		// fill image stack with interpolated image data
		interpolateMainImage();

		// setup overlays, add listener
		final Overlay ov = new Overlay();
		this.mainImp.setOverlay( ov );

		final Font font = new Font(" SansSerif", Font.PLAIN, 26);

		final TextRoi textROIA = new TextRoi(135, 12, "A", font );
		textROIA.setStrokeColor( new Color( 255, 255, 255 ) );
		ov.add( textROIA );

		final TextRoi textROIB = new TextRoi(136, 85, "B", font );
		textROIB.setStrokeColor( new Color( 255, 0, 0 ) );
		ov.add( textROIB );

		SliceListener sliceListener = l ->
		{
			final int frame = this.mainImp.getZ() - 1;

			final float c1 = (float)frame / (float)maxFrame;
			final float c2 = 1.0f - c1;

			textROIA.setStrokeColor( new Color( c2, c2, c2 ) );
			textROIB.setStrokeColor( new Color( c1, c1, c1 ) );
		};
		sliceObserver = new SliceObserver(this.mainImp, sliceListener );

		// show main window
		this.mainImp.show();

		new Thread(() ->
		{
			// default maginfication (is slow)
			Zoom.set( this.mainImp, defaultMagnification );

			// start animation
			new Animator().run( "start" );
		}).start();
	}

	public static String defaultDirectory = "";
	public static boolean defaultLoadExisting = true;

	@Override
	public void run(String arg)
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Select base directory" );

		gd.addDirectoryField("Directory", defaultDirectory, 80 );
		gd.addCheckbox( "Load existing notes", defaultLoadExisting);

		gd.showDialog();
		if ( gd.wasCanceled() )
			return;

		setup( defaultDirectory = gd.getNextString() );
		SwingUtilities.invokeLater(() -> this.showDialog( 100, 3.0, 50, Color.orange, defaultLoadExisting = gd.getNextBoolean() ) );
	}

	public static void main( String[] args )
	{
		new ImageJ();
		defaultDirectory = "/Users/preibischs/Documents/Janelia/Projects/Funke/phase1/";

		new MLTool().run( null );

		//final MLTool tool = new MLTool();
		//tool.setup( "/Users/preibischs/Documents/Janelia/Projects/Funke/phase1/" );
		//SwingUtilities.invokeLater(() -> tool.showDialog( 100, 3.0, 50, Color.orange ) );

		/*
		ImagePlus imp1 = new ImagePlus( "/Users/preibischs/Documents/Janelia/Projects/Funke/image001.png");
		//ImagePlus imp2 = new ImagePlus( "/Users/preibischs/Documents/Janelia/Projects/Funke/image002.png");
		//ImagePlus mask = new ImagePlus( "/Users/preibischs/Documents/Janelia/Projects/Funke/image003.png");
		//setImages( (ByteProcessor)imp1.getProcessor(), (ByteProcessor)imp2.getProcessor(), (ByteProcessor)mask.getProcessor() );

		Overlay ov = new Overlay();
		imp1.setOverlay( ov );
		
		final Font font = new Font(" SansSerif", Font.PLAIN, 26);
		Color color = new Color( 1.00f, 1.00f, 1.00f );

		final TextRoi textROI = new TextRoi(50, 60, "A", font );
		textROI.setStrokeColor( color );
		ov.add( textROI );

		imp1.show();

		for ( int i = 128; i < 256; ++i )
		{
			SimpleMultiThreading.threadWait( 10 );
			color = new Color(i, i, i);
			textROI.setStrokeColor( color );
			imp1.updateAndDraw();
		}
		//final OvalRoi or = new OvalRoi(20, 20, 30, 30 );
		//or.setStrokeColor( Color.red );
		//ov.add(or);
		imp1.updateAndDraw();
		*/

	}
}
