
def call(String buildStatus = 'STARTED') {
  // build status of null means successful
  buildStatus =  buildStatus ?: 'SUCCESSFUL'
  previousStatus = currentBuild.getPreviousBuild() ? currentBuild.getPreviousBuild().result : 'SUCCESSFUL'

  //Previous builds have a SUCCESS state, change to SUCCESSFUL to match current build
  if (previousStatus == 'SUCCESS') {
    previousStatus = 'SUCCESSFUL'
  }
 
  // Default values
  def colorName = 'RED'
  def colorCode = '#FF0000'
  def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
  def summary = """${subject} <br /> <b><a href="${env.RUN_DISPLAY_URL}">Review Build Results</a></b>"""
  def details = """<p>STARTED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
    <p>Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>"</p>"""
 
  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color = 'BLUE'
    colorCode = '#0000FF'
  } else if (buildStatus == 'SUCCESSFUL') {
    color = 'GREEN'
    colorCode = '#00FF00'
  } else if (buildStatus == 'UNSTABLE') {
    color = 'YELLOW'
    colorCode = '#FFFF00'
  } else {
    color = 'RED'
    colorCode = '#FF0000'
  }
 
 //Notify only on a status change
 if (previousStatus != buildStatus) {
    // Send notifications of build state change
    //slackSend (color: colorCode, message: summary)
  
    echo "Notifying that build was a ${buildStatus}"
    //hipchatSend (color: color, notify: true, message: summary)
 
    // emailext (
    //     subject: subject,
    //     body: details,
    //     recipientProviders: [[$class: 'CulpritsRecipientProvider']]
    //   )
 }
}