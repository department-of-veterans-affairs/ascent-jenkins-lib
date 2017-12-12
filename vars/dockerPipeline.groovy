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
            disableConcurrentBuilds(),
            pipelineTriggers([
                pollSCM('*/5 * * * *')
            ]),
            buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '5'))
        ])

        try {
            stage('Checkout SCM') {
                checkout scm
            }
            
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