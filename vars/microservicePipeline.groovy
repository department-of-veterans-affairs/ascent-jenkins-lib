def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }

    if (config.dockerBuilds == null) {
        config.dockerBuilds = [
                (config.imageName): config.directory
        ]
    }

    node {
        properties([
            pipelineTriggers([
                pollSCM('*/5 * * * *')
            ])
        ])

        try {

            stage('Checkout SCM') {
                checkout scm
            }

            dir("${config.directory}") {

                mavenBuild {
                    directory = config.directory
                    mavenSettings = config.mavenSettings
                }

                echo "Build Result is: ${currentBuild.result}"
                if (currentBuild.result == null) {
                    def builds = [:]
                    for (x in config.dockerBuilds.keySet()) {
                        def image = x
                        builds[image] = {
                            echo "Image Name: ${image}"
                            dockerBuild {
                                directory = config.dockerBuilds[image]
                                imageName = image
                            }
                        }
                    }

                    parallel builds

                    if (!isPullRequest() && config.testEnvironment != null) {
                        try {
                            //Deploy to CI for automated testing
                            def testEnvPort = deployStack {
                                composeFiles = config.testEnvironment
                                serviceName = config.serviceToTest
                                vaultTokens = config.vaultTokens
                            }

                            mavenFunctionalTest {
                                directory = config.directory
                                serviceUrl = "${this.env.DOCKER_SWARM_URL}:${testEnvPort}"
                                cucumberReportDirectory = config.cucumberReportDirectory
                            }
                        } catch (ex) {
                            echo "Failed due to ${ex}: ${ex.message}"
                            if (currentBuild.result == null) {
                                currentBuild.result = 'FAILED'
                            } 
                        } finally {
                            undeployStack {}
                        }
                    }
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