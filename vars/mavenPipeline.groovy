def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }

    node {
        properties([
            disableConcurrentBuilds(),
            pipelineTriggers([
                pollSCM('*/5 * * * *')
            ]),
            parameters ([
                booleanParam(name: 'isRelease', defaultValue: false, description: 'Release this build?'),
                string(name: 'release', defaultValue: '', description: 'Provide the release version'),
                string(name: 'development', defaultValue: '', description: 'Provide the next development version')
            ])
        ])
        

        try {
            stage('Checkout SCM') {
                checkout scm
            }

            mavenBuild {
                directory = config.directory
                mavenSettings = config.mavenSettings
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