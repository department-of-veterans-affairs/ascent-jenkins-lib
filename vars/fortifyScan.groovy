def call(body) {

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.directory == null) {
      config.directory = '.'
  }

  if (config.project_name == null) {
      def names = (config.directory.split("/"))
      // Get the last element (-1 wraps to the last element)
      config.project_name = names[-1]
  }

  node ('fortify-sca') {
    dir("${config.directory}") {
      stage('Fortify Analyzer') {
          sh "sourceanalyzer -b ${config.project-name} -scan"
      }
    }
  }
}
