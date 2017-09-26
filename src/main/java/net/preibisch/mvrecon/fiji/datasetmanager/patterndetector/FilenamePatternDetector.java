package net.preibisch.mvrecon.fiji.datasetmanager.patterndetector;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public interface FilenamePatternDetector
{
	public void detectPatterns(List<File> files);
	public String getInvariant(int n);
	public List<String> getValuesForVariable(int n);
	public Pattern getPatternAsRegex();
	public String getStringRepresentation();
	public int getNumVariables();
}
