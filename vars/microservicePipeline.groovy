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
                mavenRelease {
                    directory = config.directory
                    releaseVersion = this.params.releaseVersion
                    developmentVersion = this.params.developmentVersion
                }
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
                                version = this.params.releaseVersion
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
                                deployWaitTime = config.deployWaitTime
                            }

                            mavenFunctionalTest {
                                directory = config.directory
                                serviceUrl = "${this.env.DOCKER_SWARM_URL}:${testEnvPort}"
                                cucumberReportDirectory = config.cucumberReportDirectory
                                testVaultTokenRole = config.testVaultTokenRole
                                cucumberOpts = config.cucumberOpts
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

                    if (!isPullRequest() && config.perfEnvironment != null) {
                        //Aquire a lock on the performance environment so that only one performance test executes at a time
                        lock('perf-env') {
                            try {
                                //Deploy for performance testing
                                def testEnvPort = deployStack {
                                    composeFiles = config.perfEnvironment
                                    vaultTokens = config.vaultTokens
                                    deployWaitTime = 120
                                    dockerHost = "tcp://${this.env.PERF_SWARM_HOST}"
                                }

                                mavenPerformanceTest {
                                    directory = config.directory
                                    serviceProtocol = "http"
                                    serviceHost = "${this.env.PERF_SWARM_HOST}"
                                    servicePort = "${testEnvPort}"
                                    testVaultTokenRole = config.testVaultTokenRole
                                    options = config.perfTestOptions
                                }
                            } catch (ex) {
                                echo "Failed due to ${ex}: ${ex.message}"
                                if (currentBuild.result == null) {
                                    currentBuild.result = 'FAILED'
                                }
                            } finally {
                                undeployStack {
                                    dockerHost = "tcp://${this.env.PERF_SWARM_HOST}"
                                }
                            }
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
