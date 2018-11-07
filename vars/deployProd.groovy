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

  def url = ''
  def urlMinusProtocol = ''
  withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
      stage('Check for conflicts') {
        url = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
        urlMinusProtocol = url.substring(url.indexOf('://')+3)
        sh "git fetch --progress https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} +refs/heads/master:refs/remotes/origin/master"
        sh "git checkout -b master origin/master"
        // Get the tags from the origin repo
        //sh "git fetch --tags origin"
        // Do a local merge without committing anything, checking for conflicts
        try {
          sh("git merge --no-commit --no-ff tags/${config.prodVersion} | grep -v CONFLICT")
        } catch (ex) {
          // abort the merge
          sh "git merge --abort"
          error("Conflict between tag ${config.prodVersion} and master!")
        }
      }
  }

  def keystoreAlias = "prod"

  stage('Deploy to Production') {
    println("Checkout tags/${config.prodVersion}")
    // Deploy from the tag
    sh "git checkout tags/${config.prodVersion}"
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
  def isValidDeploy = true

  stage('Validate') {
    // --- Some kind of smoke testing here, if they fail, flip isValidDeploy to false
  }



  // Merge tag into master
  if (isValidDeploy) {
    stage ('Merge master') {
      print("Merging tags/${config.prodVersion} to master...")
      withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        url = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
        urlMinusProtocol = url.substring(url.indexOf('://')+3)
        sh "git checkout master"
        def isNoMerge = sh(returnStdout: true, script:"git merge --no-commit --no-ff tags/${config.prodVersion}").matches("Already up to date.*")

        // Abort the local changes if the tag is up to date with the master
        if(isNoMerge) {
          sh "git merge --abort"
        } else {
          // Go ahead and commit if the tag has changes
          sh "git commit -a -m \"Merge tag ${config.prodVersion} into master\""
          //Push the merged in tag to master branch
          sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} master"
        }
      }
    }
  } else {
    stage ("Rollback Deployment") {
      //Undeploy from the tag
      undeployStack {
          dockerHost = "${this.env.PROD_DOCKER_SWARM_MANAGER}"
          dockerDomain = this.env.DOCKER_PROD_DOMAIN
          vaultAddr = "https://${this.env.PROD_VAULT_HOST}"
          keystoreAlias = "${keystoreAlias}"
      }
      sh "git checkout master"
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
      error("Production deployment did not successfully pass smoke testing")
    }
  }
}
