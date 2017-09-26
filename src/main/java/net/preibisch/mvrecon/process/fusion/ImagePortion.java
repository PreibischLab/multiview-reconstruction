package net.preibisch.mvrecon.process.fusion;

public class ImagePortion
{
	public ImagePortion( final long startPosition, long loopSize )
	{
		this.startPosition = startPosition;
		this.loopSize = loopSize;
	}
	
	public long getStartPosition() { return startPosition; }
	public long getLoopSize() { return loopSize; }
	
	protected long startPosition;
	protected long loopSize;
	
	@Override
	public String toString() { return "Portion [" + getStartPosition() + " ... " + ( getStartPosition() + getLoopSize() - 1 ) + " ]"; }
}
