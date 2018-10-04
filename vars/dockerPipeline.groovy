def call(body) {

    def config = [:]
    def triggers = []
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }
    if (config.upstreamProjects != null) {
        triggers.add(upstream(threshold: 'SUCCESS', upstreamProjects: config.upstreamProjects))
    }

    if (config.dockerBuilds == null) {
        config.dockerBuilds = [
                (config.imageName): config.directory
        ]
    }

    if (config.replicas == null) {
        config.replicas = 3
    }

    node {
        properties([
            disableConcurrentBuilds(),
            pipelineTriggers(triggers),
            parameters ([
                booleanParam(name: 'isRelease', defaultValue: false, description: 'Release this build?'),
                string(name: 'releaseVersion', defaultValue: '', description: 'Provide the release version:'),
                string(name: 'developmentVersion', defaultValue: '', description: 'Provide the next development version:')
            ]),
            buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '5'))
        ])

        try {
            stage('Checkout SCM') {
                checkout scm
            }

            if (config.composeFiles == null && fileExists("docker-compose.yml")) {
                echo('No compose files defined for deployment. Defaulting to docker-compose.yml...')
                config.composeFiles = ["docker-compose.yml"]
            }

            if (params.isRelease) {
                //Execute maven release process and receive the Git Tag for the release
                dockerRelease {
                    directory = config.directory
                    releaseVersion = this.params.releaseVersion
                }
            }

            def builds = [:]
            for (x in config.dockerBuilds.keySet()) {
                def image = x
                builds[image] = {
                    echo "Image Name: ${image}"
                    dockerBuild {
                        directory = config.dockerBuilds[image]
                        imageName = image
                        version = this.params.releaseVersion
                    }
                }
            }

            parallel builds

            //If all the tests have passed, deploy this build to the Dev environment
            if (!isPullRequest() && currentBuild.result == null && config.composeFiles != null) {
                def devEnvPort = deployStack {
                    composeFiles = config.composeFiles
                    stackName = config.stackName
                    serviceName = config.serviceToTest
                    vaultTokens = config.vaultTokens
                    deployWaitTime = config.deployWaitTime
                    dockerHost = this.env.CI_DOCKER_SWARM_MANAGER
                    deployEnv = [
                        "SPRING_PROFILES_ACTIVE=aws-dev",
                        "ES_HOST=${this.env.DEV_ES}"
                    ]
                }
            }

            def deployments = [:]
            // Deploy platform services to performance if dev deployment was successful and
            //     if this is  a release build.
            deployments["Performance"] = {
              if (currentBuild.result == null
                            && params.isRelease
                            && config.composeFiles != null
                            && env.JOB_NAME.contains("ascent-")) {
              stage("Deploy Platform Services to Perf"){
                def perfEnvPort = deployStack {
                  composeFiles = config.composeFiles
                  stackName = config.stackName
                  serviceName = config.serviceName
                  vaultTokens = config.vaultTokens
                  deployWaitTime = config.deployWaitTime
                  dockerHost = "tcp://${this.env.PERF_SWARM_HOST}:2376"
                  dockerDomain = this.env.DOCKER_PERF_DOMAIN
                  deployEnv = [
                    "SPRING_PROFILES_ACTIVE=aws-ci",
                    "RELEASE_VERSION=${this.params.releaseVersion}",
                    "ES_HOST=${this.env.DEV_ES}",
                    "REPLICAS=${config.replicas}"
                  ]
                }
              }
            }
          }

            // If deployment to dev passed and this  is a release build, then deploy to staging
            deployments["Staging"] = {
              if (currentBuild.result == null && params.isRelease && config.composeFiles != null) {

                def stageEnvPort = deployStack {
                  composeFiles = config.composeFiles
                  stackName = config.stackName
                  serviceName = config.serviceName
                  vaultTokens = config.vaultTokens
                  deployWaitTime = config.deployWaitTime
                  dockerHost = this.env.STAGING_DOCKER_SWARM_MANAGER
                  dockerDomain = this.env.DOCKER_STAGE_DOMAIN
                  vaultAddr = "https://${this.env.STAGING_VAULT_HOST}"
                  vaultCredID = "staging-vault"
                  deployEnv = [
                    "SPRING_PROFILES_ACTIVE=aws-stage",
                    "RELEASE_VERSION=${this.params.releaseVersion}",
                    "ES_HOST=${this.env.STAGING_ES}",
                    "REPLICAS=${config.replicas}"
                    ]
                  }
                }
            }

          // deploy tp staging and perf at same time.
          parallel deployments

        } catch (ex) {
            if (currentBuild.result == null) {
                currentBuild.result = 'FAILED'
            }
            echo "Failed due to ${ex}: ${ex.message}"
        } finally {
            //Send build notifications if needed
            notifyBuild(currentBuild.result)
        }
    }
}
