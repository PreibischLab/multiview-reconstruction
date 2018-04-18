/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.Prefs;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class Threads
{
	public static int numThreads() { return Math.max( 1, Prefs.getThreads() ); }

	public static ExecutorService createFlexibleExecutorService( final int nThreads ) { return Executors.newWorkStealingPool( nThreads ); }
	public static ExecutorService createFlexibleExecutorService() { return createFlexibleExecutorService( numThreads() ); }

	public static ExecutorService createFixedExecutorService( final int nThreads ) { return Executors.newFixedThreadPool( nThreads ); }
	public static ExecutorService createFixedExecutorService() { return createFixedExecutorService( numThreads() ); }

	public static void main( String[] args )
	{
		// test if it fails on nested tasks
		final ExecutorService service = createFlexibleExecutorService();

		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( int i = 0; i < 50; ++i )
		{
			final int j = i;

			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					System.out.println( "Computing Thread (running local tasks): " + j );

					Thread.sleep( 1000 );

					final ArrayList< Callable< Void > > localtasks = new ArrayList< Callable< Void > >();

					for ( int k = 0; k < 3; ++k )
					{
						final int l = k;

						localtasks.add( new Callable< Void >()
						{
							@Override
							public Void call() throws Exception
							{
								System.out.println( "Computing local Thread: " + l + "(" + j + ")" );

								Thread.sleep( 500 );

								System.out.println( "Finished local Thread: " + l + "(" + j + ")" );

								return null;
							}
						});
					}

					FusionTools.execTasks( localtasks, service, "small thread loop " + j );

					System.out.println( "Finish Thread: " + j );

					return null;
				}
			});
		}

		FusionTools.execTasks( tasks, service, "big loop" );
	}

}
