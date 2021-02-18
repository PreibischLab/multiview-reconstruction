/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.headless.resave;

import java.io.File;

/**
 * Created by schmied on 02/07/15.
 */
public class ResaveHdf5Parameter {

    public boolean setMipmapManual = false;
    public String resolutions = "{1,1,1}, {2,2,1}, {4,4,2}";
    public String subdivisions =  "{16,16,16}, {16,16,16}, {16,16,16}";
    public File seqFile;
    public File hdf5File;
    public boolean deflate = true;
    public boolean split = false;
    public int timepointsPerPartition = 1;
    public int setupsPerPartition =1;
    public boolean onlyRunSingleJob = false;
    public int jobId = 0;

    public int convertChoice = 1;
    public double min = Double.NaN;
    public double max = Double.NaN;

    public String getXmlFilename = "one.xml";
    static int lastJobIndex = 0;
    public String exportPath = "/Users/pietzsch/Desktop/spimrec2.xml";

}
