def call(body) {

    def config = [:]
    def triggers = [pollSCM('*/5 * * * *')]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }
    if (config.upstreamProjects != null) {
        triggers.addAll(config.upstreamProjects)
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

            mavenBuild {
                directory = config.directory
                mavenSettings = config.mavenSettings
            }
        } catch (ex) {
            echo "Failed due to: ${ex.message}"
            if (currentBuild.result == null) {
                currentBuild.result = 'FAILED'
            }
        } finally {
            //Send build notifications if needed
            notifyBuild(currentBuild.result)
        }
    }

}