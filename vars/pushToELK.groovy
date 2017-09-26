// GrabResolver needed for old versions of Jenkins running Groovy 1.8 http://stackoverflow.com/questions/6335184/groovy-1-8-grab-fails-unless-i-manually-call-grape-resolve
@GrabResolver(name='http-builder', m2Compatible='true', root='http://repo1.maven.org/maven2/')
@Grapes(
   @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
)

import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import net.praqma.quticon.BuildDataEntry

// NonCPS added to avoid java.io.NotSerializableException's
// Read more here https://github.com/jenkinsci/workflow-cps-plugin#technical-design
@NonCPS
def call(def url, def index, def buildDataEntryList, def proxy_protocol=null, def proxy_host=null, def proxy_port=null) {
  mappings = """{ "mappings" : { "_default_" : { "properties" : { 
                                                                  "verdict" : {"type": "integer"},
                                                                  "job_name" : {"type": "string"},
                                                                  "build_number" : {"type": "integer"},
                                                                  "duration" : {"type": "integer"},
                                                                  "timestamp" : {"type": "date"},
                                                                  "time_in_queue" : {"type": "integer"}
  }}}}"""
  echo "Posting mappings to ${url}/${index}: ${mappings}"
  def http = new HTTPBuilder(url)
  if (proxy_host != null) {
     http.setProxy(proxy_host, proxy_port, proxy_protocol)
  }
  http.request( PUT, JSON ) { req ->
    body = mappings
    uri.path = "/${index}"
     
    response.success = { resp, json ->
        println "Success! ${resp.status}"
    }

    response.failure = { resp ->
        println "Request failed with status ${resp.status}"
    }
  }
  echo "Posting data to ${url}"
  for (BuildDataEntry entry: buildDataEntryList) {
    def data = """{"verdict": "${entry.verdict}", "duration": ${entry.duration}, "build_number": ${entry.build_number}, "timestamp": ${entry.timestamp}, "time_in_queue": ${entry.time_in_queue}}"""
    // We are using job name as a type in Elastic Search and need to do a little bit sanitanization firts
    def type = entry.job_name.replaceAll(" ", "-").toLowerCase()
    def uriPath ="${index}/${type}/${entry.build_number}"
    echo "Post ${data} to ${url}/${uriPath}"
    http.post(path:uriPath, body:data) { resp, json ->
      echo json.toString()
      echo resp.statusLine.toString()
    }
  }
}
