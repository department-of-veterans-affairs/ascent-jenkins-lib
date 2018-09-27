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
            if (!isPullRequest() && currentBuild.result == null) {
                def devEnvPort = deployStack {
                    composeFiles = config.composeFiles
                    stackName = config.stackName
                    serviceName = config.serviceToTest
                    vaultTokens = config.vaultTokens
                    deployWaitTime = config.deployWaitTime
                    dockerHost = this.env.CI_DOCKER_SWARM_MANAGER
                    deployEnv = [
                        "SPRING_PROFILE=aws-dev",
                        "ES_HOST=${this.env.DEV_ES}"
                    ]
                }
            }
        } catch (ex) {
            if (currentBuild.result == null) {
                currentBuild.result = 'FAILED'
            }
        } finally {
            //Send build notifications if needed
            notifyBuild(currentBuild.result)
        }
    }
}