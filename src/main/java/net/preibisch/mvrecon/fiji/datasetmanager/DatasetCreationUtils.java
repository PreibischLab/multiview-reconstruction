package net.preibisch.mvrecon.fiji.datasetmanager;

import java.util.HashMap;
import java.util.Map;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;

public class DatasetCreationUtils
{

	/**
	 * Assembles the {@link ViewRegistration} object consisting of a list of {@link ViewRegistration}s for all {@link ViewDescription}s that are present
	 * 
	 * @param viewDescriptionList - map from View IDs to View Descriptions
	 * @param minResolution - the smallest resolution in any dimension (distance between two pixels in the output image will be that wide)
	 * @return the View Registrations
	 */
	public static ViewRegistrations createViewRegistrations( final Map< ViewId, ViewDescription > viewDescriptionList, final double minResolution )
	{
		final HashMap< ViewId, ViewRegistration > viewRegistrationMap = new HashMap< ViewId, ViewRegistration >();
	
		for ( final ViewDescription viewDescription : viewDescriptionList.values() )
			if ( viewDescription.isPresent() )
			{
				final ViewRegistration viewRegistration = new ViewRegistration( viewDescription.getTimePointId(), viewDescription.getViewSetupId() );
				final VoxelDimensions voxelSize = viewDescription.getViewSetup().getVoxelSize(); 
	
				final double calX = voxelSize.dimension( 0 ) / minResolution;
				final double calY = voxelSize.dimension( 1 ) / minResolution;
				final double calZ = voxelSize.dimension( 2 ) / minResolution;
	
				// 1st view transform: calibration := scaling to isotropic resolution (units of length -> minResolution) 
				final AffineTransform3D m = new AffineTransform3D();
				m.set(  calX, 0.0f, 0.0f, 0.0f,
						0.0f, calY, 0.0f, 0.0f,
						0.0f, 0.0f, calZ, 0.0f );
				final ViewTransform vt = new ViewTransformAffine( "calibration", m );
				viewRegistration.preconcatenateTransform( vt );
	
				// 2nd view transform: translation to tile location (Tile has physical unit locations -> we transform to minResolution units)
				final Tile tile = viewDescription.getViewSetup().getAttribute( Tile.class );
				if (tile.hasLocation()){
					final double shiftX = tile.getLocation()[0] / voxelSize.dimension( 0 ) * calX;
					final double shiftY = tile.getLocation()[1] / voxelSize.dimension( 1 ) * calY;
					final double shiftZ = tile.getLocation()[2] / voxelSize.dimension( 2 ) * calZ;
	
					final AffineTransform3D m2 = new AffineTransform3D();
					m2.set( 1.0f, 0.0f, 0.0f, shiftX,
							0.0f, 1.0f, 0.0f, shiftY,
							0.0f, 0.0f, 1.0f, shiftZ );
					final ViewTransform vt2 = new ViewTransformAffine( "Translation", m2 );
					viewRegistration.preconcatenateTransform( vt2 );
				}
				viewRegistrationMap.put( viewRegistration, viewRegistration );
			}
		return new ViewRegistrations( viewRegistrationMap );
	}

}
