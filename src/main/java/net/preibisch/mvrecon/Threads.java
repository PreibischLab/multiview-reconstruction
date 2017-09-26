package net.preibisch.mvrecon;

import ij.Prefs;

public class Threads
{
	public static int numThreads() { return Math.max( 1, Prefs.getThreads() ); }
}
