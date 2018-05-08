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
            // -- Generate an xml report, parse, and fail if there are critical violations
            def xmlFile = "target/fortify-${config.projname}-scan.xml"
            sh "ReportGenerator -format xml -f ${xmlFile} -source ${fortifyScanResults}"

            println "reading the xml file ${env.WORKSPACE}/${xmlFile}"
            def xml = readFile "${env.WORKSPACE}/${xmlFile}"
            println "parsing the xml..."
            def reportDefinition = new XmlSlurper().parseText(xml)
            println "done parsing xml. Content :"
            
            // -- Generate a pdf report to archive with the build
            sh "ReportGenerator -format pdf -f target/fortify-${config.projname}-scan.pdf -source ${fortifyScanResults}"
            archive "target/fortify-${config.projname}-scan.pdf"
          } else {
            print "Fortify code report ${fortifyScanResults} not found. Skipping the report generator..."
          }
      }
    }
  }
}
