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
    unstash 'packaged'

    dir("${config.directory}") {
      stage('Debug') {
        echo "projname=${config.projname}"
        echo "directory=${config.directory}"
      }

      stage('Fortify Analyzer') {
          sh "sourceanalyzer -b ${config.projname} -clean"
          sh "sourceanalyzer -b ${config.projname} ."
          sh "sourceanalyzer -b ${config.projname} -scan"
      }
    }
  }
}
