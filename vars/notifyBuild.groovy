
def call(String buildStatus = 'STARTED') {
  // build status of null means successful
  buildStatus =  buildStatus ?: 'SUCCESS'
  previousStatus = currentBuild.getPreviousBuild() ? currentBuild.getPreviousBuild().result : 'SUCCESS'

  // Default values
  def colorName = 'RED'
  def colorCode = '#FF0000'
  def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
  def summary = """${subject} <br /> <b><a href="${env.RUN_DISPLAY_URL}">Review Build Results</a></b>"""
  def details = """<p>${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
    <p>Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>"</p>"""

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    color = 'BLUE'
    colorCode = '#0000FF'
  } else if (buildStatus == 'SUCCESS') {
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
  if (previousStatus != buildStatus || buildStatus == 'UNSTABLE' || buildStatus == 'FAILURE') {

    echo "Notifying that build was a ${buildStatus}"
    // Send notifications of build state change
    slackSend (color: colorCode, message: summary)

    //hipchatSend (color: color, notify: true, message: summary)

    emailext (
      subject: subject,
      body: details,

      recipientProviders: [[$class: 'CulpritsRecipientProvider'],
        [$class: 'RequesterRecipientProvider'],
        [$class: 'FailingTestSuspectsRecipientProvider'],
        [$class: 'FirstFailingBuildSuspectsRecipientProvider']]
    )
  }
}
