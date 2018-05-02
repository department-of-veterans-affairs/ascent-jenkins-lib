def call(body) {

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.directory == null) {
      config.directory = '.'
  }

  if (config.projname == null) {
      config.projname = "${env.JOB_NAME}"
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
          sh "sourceanalyzer -b ${config.projname}"
          sh "sourceanalyzer -b ${config.projname} -scan -f target/fortify-${config.projname}-scan.fpr -format fpr"
          sh "ReportGenerator -format xml -f target/fortify-${config.projname}-scan.xml -source target/fortify-${config.projname}-scan.fpr"
          archive "target/fortify-${config.projname}-scan.xml"
      }

    }
  }
}
