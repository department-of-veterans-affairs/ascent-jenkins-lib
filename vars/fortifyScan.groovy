def call(body) {

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

  node ('fortify-sca') {
    // unstash the packages from the mavenBuild on other node
    unstash "packaged"

    dir("${config.directory}") {
      stage('Debug') {
        echo "projname=${config.projname}"
        echo "directory=${config.directory}"
      }

      def tmpDir = pwd(tmp: true)

      if (config.mavenSettings == null) {
          config.mavenSettings = "${tmpDir}/settings.xml"
          stage('Configure Maven') {
              def mavenSettings = libraryResource 'gov/va/maven/settings.xml'
              writeFile file: config.mavenSettings, text: mavenSettings
          }
      }

      def mvnCmd = "mvn -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dmaven.wagon.http.ssl.allowall=true -Ddockerfile.skip=true -DskipITs=true -s ${config.mavenSettings}"
      def translateCmd = "sourceanalyzer -b ${config.projname} touchless ${mvnCmd} com.hpe.security.fortify.maven.plugin:sca-maven-plugin:17.20:translate"


      stage('Fortify Analyzer') {
          sh "${mvnCmd} dependency:resolve"
          sh "sourceanalyzer -b ${config.projname} -clean"
          sh "${translateCmd}"
          def fortifyScanResults = "target/fortify-${config.projname}-scan.fpr"

          sh "sourceanalyzer -b ${config.projname} -scan -f ${fortifyScanResults} -format fpr"

          // -- Check if a fortifyScan was generated, and if it was, then use the report generator to convert
          //    it to a pdf
          if(fileExists("${fortifyScanResults}")) {
            // -- Use the FPR utility to see if there are any issues
            def criticalIssueFile = "target/critical-issues.txt"
            sh "FPRUtility -information -categoryIssueCounts -project ${fortifyScanResults} -search -query \"[fortify priority order]:Critical\" -listIssues -f ${criticalIssueFile}"
            def criticalOuput = readFile "${criticalIssueFile}"
            // -- Check if there are critical issue in the file
            if(isCriticalIssue("${criticalOutput}")) {
              print "THERE ARE CRITICAL ISSUES!!!!!!!"
            }

            // -- Generate a pdf report to archive with the build
            sh "ReportGenerator -format pdf -f target/fortify-${config.projname}-scan.pdf -source target/fortify-${config.projname}-scan.fpr"
            archive "target/fortify-${config.projname}-scan.pdf"

          } else {
            print "Fortify code report ${fortifyScanResults} not found. Skipping the report generator..."
          }
      }
    }
  }
}
