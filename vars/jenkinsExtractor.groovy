import net.praqma.quticon.BuildDataEntry
import org.jenkinsci.plugins.workflow.job.WorkflowRun
// This is dependency to https://github.com/jenkinsci/pipeline-stage-view-plugin
// There is no good reason to extract pipeline stages from WorkflowRun on our own
import com.cloudbees.workflow.rest.external.RunExt

// This method takes a list of job names and a time interval in hours
// and returns list of BuildDataEntry
// NonCPS added to avoid java.io.NotSerializableException's
// Read more here https://github.com/jenkinsci/workflow-cps-plugin#technical-design
@NonCPS
def call(def jobNames, def numberOfHoursBack) {
	def buildResults = []
	if (jobNames==[]) {
		echo "No job names specified for extraction. Check all jobs"
		jobNames = Jenkins.instance.getJobNames()
	}
    	for (def jobName: jobNames) {
		echo "Looking for the job with the name $jobName"
		def job = Jenkins.instance.getItemByFullName(jobName)
  		if (job == null) {
          		echo "Job ${jobName} wasn't found. Check your configuration"
			continue
        	}
		def builds = job.getBuilds().byTimestamp(System.currentTimeMillis()-numberOfHoursBack*60*60*1000, System.currentTimeMillis()).completedOnly()
    		echo "Found ${builds.size()} builds matching time criteria for the job ${jobName}"
		for (def build: builds) {
			// Make sure we remove / from job name since it will be part of URL and might confuse ELK
			sanitizedJobName = jobName.replaceAll("/", ".")
			// If this is a pipeline then extract pipeline stages as separate entries in addition to the pipeline run itself that will
			// be extracted in the next step
			if (build instanceof WorkflowRun) {
				for (flowNode in RunExt.create(build).getStages()) {
					 // SUCCESS and FAILURE Verdicts need to be in numeric values
					def build_verdict = getBuildVerdictInteger(flowNode.getStatus())
					
					def stage_entry = new BuildDataEntry(job_name: "${sanitizedJobName}.${flowNode.getName().replaceAll("[^a-zA-Z0-9]", "_")}", 
			           		   verdict: flowNode.getStatus(),
			                           build_number: build_verdict,
				                   duration: flowNode.getDurationMillis(), 
				                   timestamp: flowNode.getStartTimeMillis(),
						   time_in_queue: 0) // Fixme!
					echo "New pipeline stage entry: name ${stage_entry.job_name}, result ${stage_entry.verdict}, number ${stage_entry.build_number}, duration ${stage_entry.duration}, timestamp ${stage_entry.timestamp}, time in queue ${stage_entry.time_in_queue}"
            				buildResults.add(stage_entry)
				}
			}
			 def entry = new BuildDataEntry(job_name: sanitizedJobName, 
			           		   verdict: build.result,
			                           build_number: build.number,
				                   duration: build.duration, 
				                   timestamp: build.getTimeInMillis(),
						   time_in_queue: build.getStartTimeInMillis() - build.getTimeInMillis())
			echo "New entry: name ${entry.job_name}, result ${entry.verdict}, number ${entry.build_number}, duration ${entry.duration}, timestamp ${entry.timestamp}, time in queue ${entry.time_in_queue}"
            		buildResults.add(entry)
        	}
	}
	return buildResults
}

def getBuildVerdictInteger(def verdictString){
	def returnInteger = 0
	if(verdictString == "SUCCESS"){
	   returnInteger = 0
	   }
	   else if(verdictString == "FAILURE"){
		   returnInteger = 1
	   }
	  return returnInteger
}
