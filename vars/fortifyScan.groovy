def call(body) {

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.directory == null) {
      config.directory = '.'
  }

  if (config.project-name == null) {
      def names = (config.directory.split("/")
      // Get the last element (-1 wraps to the last element)
      config.project-name = names[-1]
  }

    dir("${config.directory}") {
      sh "sourceanalyzer -b ${config.project-name} -scan"
    }
}
