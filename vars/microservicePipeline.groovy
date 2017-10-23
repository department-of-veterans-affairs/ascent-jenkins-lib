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
            }
        } finally {
            //Send build notifications if needed
            notifyBuild(currentBuild.result)
        }
    }

}