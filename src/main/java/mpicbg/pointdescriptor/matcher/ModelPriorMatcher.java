package mpicbg.pointdescriptor.matcher;

import java.util.ArrayList;

import net.preibisch.mvrecon.vecmath.Matrix3d;
import net.preibisch.mvrecon.vecmath.Point3d;
import net.preibisch.mvrecon.vecmath.Quat4d;
import net.preibisch.mvrecon.vecmath.Quat4f;
import net.preibisch.mvrecon.vecmath.Vector3f;

import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel3D;
import mpicbg.pointdescriptor.model.TranslationInvariantRigidModel3D;
import mpicbg.util.TransformUtils;

/**
 * 
 * This class does a simple matching but computes a normalizationFactor based a given model prior.
 * 
 * @author Stephan Preibisch (preibisch@mpi-cbg.de)
 *
 */
public class ModelPriorMatcher extends SimpleMatcher
{
	final RigidModel3D model;
	final Point3d referenceAxis;
	final double angle;
	
	final Matrix3d referenceMatrix, invertedReferenceMatrix;
	
	public ModelPriorMatcher( final int numNeighbors, final RigidModel3D model )
	{
		super( numNeighbors );
	
		this.model = model;
		
		this.referenceMatrix = new Matrix3d();                
        TransformUtils.getTransform3D( model ).get( referenceMatrix );

        this.invertedReferenceMatrix = new Matrix3d( this.referenceMatrix );
		this.invertedReferenceMatrix.invert();

		final Quat4f quaternion = new Quat4f();	     
        quaternion.set( referenceMatrix );
        
        this.angle = (float)Math.toDegrees( Math.acos( quaternion.getW() ) * 2 );
        final Vector3f axis = new Vector3f( quaternion.getX(), quaternion.getY(), quaternion.getZ() );
        axis.normalize();        
        this.referenceAxis = new Point3d( axis );
  	}

	@Override
	public double getNormalizationFactor( final ArrayList<PointMatch> matches, final Object fitResult ) 
	{
		final TranslationInvariantRigidModel3D matchModel = (TranslationInvariantRigidModel3D)fitResult;
		
		/* get input matrices and quaternion that we can alter */
		final Quat4d quaternion = new Quat4d();
		final Matrix3d templateMatrix = new Matrix3d();
		matchModel.getMatrix3d( templateMatrix );
		
		/* Compute the rotation angle between the two rigid 3d transformations */
		templateMatrix.mul( invertedReferenceMatrix );		
        quaternion.set( templateMatrix );
        
        final double angle = Math.max( 5, Math.toDegrees( Math.acos( quaternion.getW() )  * 2 ) ) - 5;
        
        /* Compute vector difference between the two rotation axes */
        //final Vector3f axis = new Vector3f( quaternion.getX(), quaternion.getY(), quaternion.getZ() );
        //axis.normalize();        
       	//final Point3f templateAxis = new Point3f( axis );
        //final float difference = templateAxis.distance( referenceAxis );

        final double weight = ( 1.0f + 0.03f * angle * angle );
        
        
		return weight; 
        //return Math.pow( 10, difference );	
	}
	
}
