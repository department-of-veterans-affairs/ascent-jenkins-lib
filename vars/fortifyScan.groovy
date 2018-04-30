def call(body) {

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.directory == null) {
      config.directory = '.'
  }

  if (config.projname == null) {
      def names = (config.directory.split("/"))
      // Get the last element (-1 wraps to the last element)
      config.projname = names[-1]
  }

  node ('fortify-sca') {
    dir("${config.directory}") {
      stage('Fortify Analyzer') {
          sh "sourceanalyzer -b ${config.projname} -scan"
      }
    }
  }
}
