def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.prodVersion == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because production version is not specified')
  }

  if (config.composeFiles == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because compose files undefined')
  }

  if (config.stackName == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because stack name undefined')
  }

  if (config.serviceName == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because serviceName undefined')
  }

  if (config.vaultTokens == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because vaultTokens undefined')
  }

  if (config.directory == null) {
      config.directory = '.'
  }


  stage('Checkout Tag') {
    sh "git checkout tags/${config.prodVersion}"
  }

  def keystoreAlias = "prod"

  stage('Deploy to Production') {
    def prodEnvPort = deployStack {
      composeFiles = config.composeFiles
      stackName = config.stackName
      serviceName = config.serviceName
      vaultTokens = config.vaultTokens
      deployWaitTime = config.deployWaitTime
      keystoreAlias = "${keystoreAlias}"
      dockerHost = this.env.PROD_DOCKER_SWARM_MANAGER
      dockerDomain = this.env.DOCKER_PROD_DOMAIN
      vaultAddr = "https://${this.env.PROD_VAULT_HOST}"
      vaultCredID = "prodvault"
      deployEnv = [
        "SPRING_PROFILES_ACTIVE=awsprod",
        "RELEASE_VERSION=${this.prodVersion}",
        "ES_HOST=${this.env.PROD_ES}",
        "REPLICAS=3"
      ]
    }
  }

  def prodHost = this.env.PROD_DOCKER_SWARM_MANAGER[6..-6]

  // --- Not really sure what tests to run on prod, but for now maybe functional and perf?
  stage('Validate') {
    // mavenFunctionalTest {
    //     directory = config.directory
    //     serviceUrl = "${prodHost}:${prodEnvPort}"
    //     cucumberReportDirectory = config.cucumberReportDirectory
    //     testVaultTokenRole = config.testVaultTokenRole
    //     cucumberOpts = config.cucumberOpts
    //     options = config.intTestOptions
    //     keystore = "${this.env.DOCKER_CERT_LOCATION}/${keystoreAlias}.jks"
    //     keystorePassword = "changeit"
    // }
    //
    // mavenPerformanceTest {
    //     directory = config.directory
    //     serviceProtocol = "https"
    //     serviceHost = "${prodHost}"
    //     servicePort = "${prodEnvPort}"
    //     testVaultTokenRole = config.testVaultTokenRole
    //     options = config.perfTestOptions
    //     keystore = "${this.env.DOCKER_CERT_LOCATION}/${keystoreAlias}.jks"
    //     keystorePassword = "changeit"
    // }
  }

  stage('Push to Master') {
    withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        //Push the release version to master branch
        sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} master"
    }
  }

}
