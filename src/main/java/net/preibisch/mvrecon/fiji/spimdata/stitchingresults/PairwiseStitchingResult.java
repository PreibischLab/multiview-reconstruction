package net.preibisch.mvrecon.fiji.spimdata.stitchingresults;

import mpicbg.spim.data.registration.ViewRegistration;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;



public class PairwiseStitchingResult <C extends Comparable< C >>
{
	
	public interface PairwiseQualityMeasure
	{
		public double getQuality();
	}
	
	public class CrossCorrelationPairwiseQualityMeasure implements PairwiseQualityMeasure
	{

		private double r;
		
		public CrossCorrelationPairwiseQualityMeasure(double r)
		{
			this.r = r;
		}
		
		@Override
		public double getQuality(){ return r; }
		
	}

	final private Pair< Group<C>, Group<C> > pair;
	final private AffineTransform3D transform;
	final private RealInterval boundingBox;
	final private double r;
	final private double hash;

	/**
	 * 
	 * @param pair - what was compared
	 * @param boundingBox - in which bounding box (in global space) was is compared
	 * @param transform - the transformation mapping A to B
	 * @param r - the correlation
	 * @param hash - a hash value of the previous view registrations (at the time the *relative* pairwise shift was calculated)
	 */
	public PairwiseStitchingResult(
			final Pair< Group<C>, Group<C> > pair,
			final RealInterval boundingBox,
			final AffineGet transform,
			final double r,
			final double hash)
	{
		this.pair = pair;
		this.boundingBox = boundingBox;
		this.transform = new AffineTransform3D();
		this.transform.set( transform.getRowPackedCopy() );
		this.r = r;
		this.hash = hash;
	}

	public double getHash() {return hash;}
	public Pair< Group<C>, Group<C> > pair() { return pair; }
	public AffineGet getTransform() { return transform; }
	public double r() { return r; }
	public AffineGet getInverseTransform(){ return transform.inverse(); }
	public RealInterval getBoundingBox(){ return boundingBox; }

	/**
	 * get a very simple hash of two ViewRegistrations
	 * @param vrA first ViewRegistration
	 * @param vrB second ViewRegistration
	 * @return a hash
	 */
	public static double calculateHash(final ViewRegistration vrA, final ViewRegistration vrB)
	{
		vrA.updateModel();
		vrB.updateModel();
		final double[] rowPackedCopyVrA = vrA.getModel().getRowPackedCopy();
		final double[] rowPackedCopyVrB = vrB.getModel().getRowPackedCopy();
		double hash = 0;
		for (int i=0; i<rowPackedCopyVrA.length; i++)
			hash += (rowPackedCopyVrA[i] + 13 * rowPackedCopyVrB[i]);
		return hash;
	}
}
