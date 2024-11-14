package util;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
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
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.Animator;
import ij.plugin.Zoom;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import net.imglib2.type.numeric.ARGBType;

public class MLTool
{
	// Done: default Mask: 0.5, red
	// Done: on start ask for root directory
	// Done: zoom to 300%
	// Done: play button (with FPS) > turn into 4D image
	// Done: support images: 
	// directory a,b,m: increasing ID, no leading zeros
	// Done: save in the parent directory
	// TODO: first iteration a single text file
	
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

	final ByteProcessor[] imgsA, imgsB, imgsM;

	public MLTool( final String dir )
	{
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

			imgsA[ i ] = (ByteProcessor)impA.getProcessor();
			imgsB[ i ] = (ByteProcessor)impB.getProcessor();
			imgsM[ i ] = (ByteProcessor)mask.getProcessor();
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

	public void showDialog( final int maxFrame, final double defaultMagnification, final int defaultMask, final Color defaultColor )
	{
		setColor( defaultColor );

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
		text2 = new JButton( "Image " + imgsA.length );
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
		dialog.add( save, c );

		c.gridx = 3;
		c.gridy = 2;
		c.gridwidth = 1;
		quit = new JButton( "Quit" );
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

		dialog.pack();
		dialog.setVisible(true);

		this.main = new HashMap<>();
		this.stack = new ImageStack();

		for ( int f = 0; f <= maxFrame; ++f )
		{
			final ColorProcessor cp = new ColorProcessor( imgsA[ 0 ].getWidth(), imgsA[ 0 ].getHeight());
			this.main.put( f, cp );
			this.stack.addSlice( cp );
		}

		this.mainImp = new ImagePlus( impDialog + "0", this.stack );

		final Calibration cal = this.mainImp.getCalibration();
		cal.fps = 25;
		cal.loop = true;

		interpolateMainImage();

		this.mainImp.show();

		new Thread(() ->
		{
			Zoom.set( this.mainImp, defaultMagnification );
			new Animator().run( "start" );
		}).start();

	}

	public static void main( String[] args )
	{
		new ImageJ();

		final MLTool tool = new MLTool( "/Users/preibischs/Documents/Janelia/Projects/Funke/phase1/" );

		//ImagePlus imp1 = new ImagePlus( "/Users/preibischs/Documents/Janelia/Projects/Funke/image001.png");
		//ImagePlus imp2 = new ImagePlus( "/Users/preibischs/Documents/Janelia/Projects/Funke/image002.png");
		//ImagePlus mask = new ImagePlus( "/Users/preibischs/Documents/Janelia/Projects/Funke/image003.png");
		//setImages( (ByteProcessor)imp1.getProcessor(), (ByteProcessor)imp2.getProcessor(), (ByteProcessor)mask.getProcessor() );

		SwingUtilities.invokeLater(() -> tool.showDialog( 100, 3.0, 50, Color.orange ) );

	}
}