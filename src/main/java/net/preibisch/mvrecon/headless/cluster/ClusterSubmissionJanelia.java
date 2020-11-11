package net.preibisch.mvrecon.headless.cluster;

public class ClusterSubmissionJanelia implements ClusterSubmission
{
	public static int threadsJanelia = 8;

	@Override
	public String getSubmissionCommand( final String jobName, final String jobFileName )
	{
		return "bsub -n " + threadsJanelia + " -J " + jobName + 
				" -o /dev/null '" + jobFileName + " > " + jobFileName + ".log'";
	}

}
