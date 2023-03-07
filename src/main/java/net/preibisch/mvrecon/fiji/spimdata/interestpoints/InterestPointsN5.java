package net.preibisch.mvrecon.fiji.spimdata.interestpoints;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;

public class InterestPointsN5 extends InterestPoints
{
	final String n5path;
	ArrayList< InterestPoint > interestPoints;
	ArrayList< CorrespondingInterestPoints > correspondingInterestPoints;

	protected InterestPointsN5( final File baseDir, final String n5path )
	{
		super(baseDir);
		this.n5path = n5path;
	}

	public String getN5path() { return n5path; }

	@Override
	public String getXMLRepresentation() {
		// a hack so that windows does not put its backslashes in
		return getN5path().toString().replace( "\\", "/" );
	}

	@Override
	public String createXMLRepresentation( final ViewId viewId, final String label )
	{
		return new File( "interestpoints.n5", "tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "/" + label ).getPath();
	}

	/**
	 * @return - a list of interest points (copied), tries to load from disc if null
	 */
	@Override
	public synchronized List< InterestPoint > getInterestPointsCopy()
	{
		if ( this.interestPoints == null )
			loadInterestPoints();

		final ArrayList< InterestPoint > list = new ArrayList< InterestPoint >();

		for ( final InterestPoint p : this.interestPoints )
			list.add( new InterestPoint( p.id, p.getL().clone() ) );

		return list;
	}

	/**
	 * @return - the list of corresponding interest points (copied), tries to load from disc if null
	 */
	public synchronized List< CorrespondingInterestPoints > getCorrespondingInterestPointsCopy()
	{
		if ( this.correspondingInterestPoints == null )
			loadCorrespondences();

		final ArrayList< CorrespondingInterestPoints > list = new ArrayList< CorrespondingInterestPoints >();

		for ( final CorrespondingInterestPoints p : this.correspondingInterestPoints )
			list.add( new CorrespondingInterestPoints( p ) );

		return list;
	}

	@Override
	protected void setInterestPointsLocal( final List< InterestPoint > list )
	{
		if ( list.getClass().isInstance( ArrayList.class ))
			this.interestPoints = (ArrayList<InterestPoint>)list;
		else
			this.interestPoints = new ArrayList<>( list );
	}

	@Override
	protected void setCorrespondingInterestPointsLocal( final List< CorrespondingInterestPoints > list )
	{
		if ( list.getClass().isInstance( ArrayList.class ))
			this.correspondingInterestPoints = (ArrayList<CorrespondingInterestPoints>)list;
		else
			this.correspondingInterestPoints = new ArrayList<>( list );
	}

	public String ipDataset() { return new File( getN5path(), "interestpoints" ).getPath(); }
	public String corrDataset() { return new File( getN5path(), "correspondences" ).getPath(); }

	@Override
	public boolean saveInterestPoints( final boolean forceWrite )
	{
		if ( !modifiedInterestPoints && !forceWrite )
			return true;

		final ArrayList< InterestPoint > list = this.interestPoints;

		if ( list == null )
			return false;

		final String dataset = ipDataset();

		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( baseDir.getAbsolutePath() );

			if (n5Writer.exists(dataset))
				n5Writer.remove(dataset);
			
			n5Writer.createDataset(
					dataset,
					new long[] {1},
					new int[] {1},
					DataType.OBJECT,
					new GzipCompression());

			final DatasetAttributes datasetAttributes = n5Writer.getDatasetAttributes(dataset);

			n5Writer.writeSerializedBlock(
					list,
					dataset,
					datasetAttributes,
					new long[] {0});

			n5Writer.close();
		}
		catch (IOException e)
		{
			IOFunctions.println("Couldn't write interestpoints to N5 '" + baseDir + ":/" + dataset + "': " + e );
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	public boolean saveCorrespondingInterestPoints(boolean forceWrite)
	{
		if ( !modifiedCorrespondingInterestPoints && !forceWrite )
			return true;

		final ArrayList< CorrespondingInterestPoints > list = this.correspondingInterestPoints;

		if ( list == null )
			return false;

		final String dataset = corrDataset();

		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( baseDir.getAbsolutePath() );

			if (n5Writer.exists(dataset))
				n5Writer.remove(dataset);
			
			n5Writer.createDataset(
					dataset,
					new long[] {1},
					new int[] {1},
					DataType.OBJECT,
					new GzipCompression());

			final DatasetAttributes datasetAttributes = n5Writer.getDatasetAttributes(dataset);

			n5Writer.writeSerializedBlock(
					list,
					dataset,
					datasetAttributes,
					new long[] {0});

			n5Writer.close();
		}
		catch (IOException e)
		{
			IOFunctions.println("Couldn't write corresponding interestpoints to N5 '" + baseDir + ":/" + dataset + "': " + e );
			e.printStackTrace();
			return false;
		}

		return true;
	}

	@Override
	protected boolean loadInterestPoints()
	{
		try
		{
			final N5FSReader n5 = new N5FSReader( baseDir.getAbsolutePath() );
			final String dataset = ipDataset();

			if (!n5.exists(dataset))
			{
				IOFunctions.println( "InterestPointsN5.loadInterestPoints(): dataset '" + baseDir + ":/" + dataset + "' does not exist, cannot load interestpoints." );
				return false;
			}

			final DatasetAttributes datasetAttributes = n5.getDatasetAttributes(dataset);

			this.interestPoints = n5.readSerializedBlock( dataset, datasetAttributes, 0 );
			modifiedInterestPoints = false;

			n5.close();

			return true;
		} 
		catch ( final Exception e )
		{
			this.interestPoints = new ArrayList<>();
			IOFunctions.println( "InterestPointsN5.loadInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	protected boolean loadCorrespondences()
	{
		try
		{
			final N5FSReader n5 = new N5FSReader( baseDir.getAbsolutePath() );
			final String dataset = corrDataset();

			if (!n5.exists(dataset))
			{
				IOFunctions.println( "InterestPointsN5.loadCorrespondingInterestPoints(): dataset '" + baseDir + ":/" + dataset + "' does not exist, cannot load interestpoints." );
				return false;
			}

			final DatasetAttributes datasetAttributes = n5.getDatasetAttributes(dataset);

			this.correspondingInterestPoints = n5.readSerializedBlock( dataset, datasetAttributes, 0 );
			modifiedCorrespondingInterestPoints = false;

			n5.close();

			return true;
		} 
		catch ( final Exception e )
		{
			this.interestPoints = new ArrayList<>();
			IOFunctions.println( "InterestPointsN5.loadCorrespondingInterestPoints(): " + e );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean deleteInterestPoints()
	{
		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( baseDir.getAbsolutePath() );
	
			if (n5Writer.exists(ipDataset()))
				n5Writer.remove(ipDataset());
	
			n5Writer.close();

			return true;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "InterestPointsN5.deleteInterestPoints(): " + e );
			e.printStackTrace();

			return false;
		}
	}

	@Override
	public boolean deleteCorrespondingInterestPoints()
	{
		try
		{
			final N5FSWriter n5Writer = new N5FSWriter( baseDir.getAbsolutePath() );
	
			if (n5Writer.exists(corrDataset()))
				n5Writer.remove(corrDataset());
	
			n5Writer.close();

			return true;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "InterestPointsN5.deleteCorrespondingInterestPoints(): " + e );
			e.printStackTrace();

			return false;
		}
	}

}
