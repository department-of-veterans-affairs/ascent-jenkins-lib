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
    sh "git checkout master"
  }

  def url = ''
  def urlMinusProtocol = ''
  withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
      stage('Check master branch') {
          url = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
          urlMinusProtocol = url.substring(url.indexOf('://')+3)

          sh "git fetch --no-tags --progress https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} +refs/tags/${config.prodVersion}:refs/remotes/origin/master"

          //Compare to master branch to look for any unmerged changes in the tag
          def commitsBehind = sh(returnStdout: true, script: "git rev-list --right-only --count refs/tags/${config.prodVersion}...remotes/origin/master").trim().toInteger()
          if (commitsBehind > 0) {
              error("Master Branch has changesets not included in this tag ${config.prodVersion}. Please merge master into the tag before deploying to prod.")
          } else {
              echo "Tag ${config.prodVersion} is up to date with changesets on master. Proceeding with release..."
          }

          //Let's do the same thing with master too, just in case
          def commitsBehind = sh(returnStdout: true, script: "git rev-list --right-only --count refs/heads/master...remotes/origin/master").trim().toInteger()
          if (commitsBehind > 0) {
              error("Master Branch has changesets not included in this branch. Please merge master into this branch before deploying to prod.")
          } else {
              echo "Branch is up to date with changesets on master. Proceeding with release..."
          }

          sh "git fetch https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} +refs/tags/${config.prodVersion}:refs/tags/${config.prodVersion}"

          // Merge tag into master
          sh "git merge tags/${config.prodVersion}"
      }

      stage('Push to master branch'){
        //Push the release version to master branch
        sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} master"
      }
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
        "SPRING_PROFILES_ACTIVE=aws-prod",
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

}
