def call(body) {
  echo "called fortify stage"
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.directory == null) {
      config.directory = '.'
  }

  if (config.projname == null) {
      config.projname = "${env.JOB_BASE_NAME}"
  }

  if(config.notifyMessages == null) {
    def notifyMessages = []
  }

  if(config.artifactId == null) {
    artifactId = readMavenPom().getArtifactId()
  }

  if(config.version == null) {
    version = readMavenPom().getVersion()
  }

  if(config.applyGates == null) {
    applyGates = true
  }

  if(config.failOnGates == null) {
    failOnGates = true
  }

  if (config.mavenSettings == null) {
      config.mavenSettings = "${tmpDir}/settings.xml"
      stage('Configure Maven') {
          def mavenSettings = libraryResource 'gov/va/maven/settings.xml'
          writeFile file: config.mavenSettings, text: mavenSettings
      }
  }

  node('fortify-sca') {
    echo "in fortify node"
    stage ('Debug'){
      sh "which mvn"
      sh "which ant"
      sh "ant -version"
      sh "mvn -v"
    }

    stage ('Fortify'){
        lock(resource: "lock_fortify_${env.NODE_NAME}_${artifactId}") {
            // unstash the packages from the mavenBuild on other node
            unstash "packaged"

            dir("${config.directory}") {
              //perform fortify scan
              sh "ant -f mdm-cuf-core-fortify.xml fortify.all -Dmvn.cmd.fortify.prereq=initialize -Dproject.settings=${config.mavenSettings}"

              //generate pdf
              sh "cd \${WORKSPACE}; TEMPLATE=\"\$(dirname \$(which ReportGenerator))/../Core/config/reports/DeveloperWorkbook.xml\"; SOURCE=\"\$(find . -name \\${artifactId}*.fpr)\"; TARGET=\"target/fortify/\$(find . -name \\${artifactId}*.fpr -exec basename -s .fpr {} \\;).pdf\"; ReportGenerator -template \$TEMPLATE -format pdf -source \$SOURCE -f \$TARGET"

              sh "echo \"\n\n--------------------------------------------------\" > ${WORKSPACE}/target/fortify/fortify-gate.txt"
              sh "echo \"Fortify Gates Overview\n${artifactId}-${version}\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"
              sh "echo \"\n--------------------------------------------------\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"

              //fortify "critical" gate
              sh "echo \"\n\n-------------------------\nCritical Issues\n-------------------------\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"
              sh "FPRUtility -project \"\$(find . -name \\${artifactId}*.fpr)\" -categoryIssueCounts -listIssues -information -search -query \"[fortify priority order]:critical AND [analysis]:<none>\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"

              //fortify "high" gate
              sh "echo \"\n\n-------------------------\nHigh Issues\n-------------------------\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"
              sh "FPRUtility -project \"\$(find . -name \\${artifactId}*.fpr)\" -categoryIssueCounts -listIssues -information -search -query \"[fortify priority order]:high AND [analysis]:<none>\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"

              //fortify "code quality" gate
              sh "echo \"\n\n-------------------------\nCode Quality Issues\n-------------------------\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"
              sh "FPRUtility -project \"\$(find . -name \\${artifactId}*.fpr)\" -categoryIssueCounts -listIssues -information -search -query \"[kingdom]:code quality AND [analysis]:<none>\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"

              //fortify "don't suppress stuff" gate
              sh "echo \"\n\n-------------------------\nSuppressed Issues\n-------------------------\" >> ${WORKSPACE}/target/fortify/fortify-gate.txt"
              sh "FPRUtility -project \"\$(find . -name \\${artifactId}*.fpr)\" -categoryIssueCounts -listIssues -information -search -query \"[suppressed]:true\" -includeSuppressed >> ${WORKSPACE}/target/fortify/fortify-gate.txt"

              //archive the artifacts for easy review/accessibility
              archiveArtifacts 'target/fortify/*.pdf,target/fortify/*.fpr,target/fortify/*gate.txt'

              if(applyGates){
                  fortifyGatesPassed=true
                  fortifyGate = readFile('target/fortify/fortify-gate.txt')
                  echo fortifyGate

                  if(fortifyGate.contains("Total for all categories")){
                      gateMessage = "You have failed the Fortify Quality Gate due 1 or more issues found in your FPR."
                      echo gateMessage
                      notifyMessages.add(gateMessage)
                      notifyMessages.add(fortifyGate)
                      if(failOnGates){
                          error(gateMessage)
                      } else {
                          currentBuild.result="UNSTABLE"
                      }
                  } else {
                      echo "PASSED FORTIFY GATES!"
                  }
              } else {
                  echo "FORTIFY GATES NOT APPLIED!"
              }

              return notifyMessages
            }

          }
      }
    }
}
