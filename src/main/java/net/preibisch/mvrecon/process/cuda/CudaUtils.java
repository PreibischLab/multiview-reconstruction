package net.preibisch.mvrecon.process.cuda;

public class CudaUtils {
	final public static int[] getCUDACoordinates( final int[] c )
	{
		final int[] cuda = new int[ c.length ];

		for ( int d = 0; d < c.length; ++d )
			cuda[ c.length - d - 1 ] = c[ d ];

		return cuda;
	}
}
