def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }

    throttle([env.JOB_NAME]) {
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
                
                dockerBuild {
                    directory = config.directory
                    imageName = config.imageName
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
}