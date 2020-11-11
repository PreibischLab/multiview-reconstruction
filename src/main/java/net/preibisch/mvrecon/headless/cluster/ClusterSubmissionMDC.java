package net.preibisch.mvrecon.headless.cluster;

public class ClusterSubmissionMDC implements ClusterSubmission
{

	@Override
	public String getSubmissionCommand( final String jobName, final String jobFileName )
	{
		return "qsub -l h_vmem=34G " + jobFileName;
	}

}
