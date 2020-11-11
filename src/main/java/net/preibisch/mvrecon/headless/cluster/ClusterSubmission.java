package net.preibisch.mvrecon.headless.cluster;

public interface ClusterSubmission
{
	public String getSubmissionCommand( final String jobName, final String jobFileName );
}
