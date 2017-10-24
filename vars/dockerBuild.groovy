def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }
    if (config.imageName == null) {
        error "imageName parameter was not specified for dockerBuild"
    }

    dir("${config.directory}") {
        stage("Build ${config.imageName}") {
            docker.withServer("${env.DOCKER_HOST}") {
                docker.withRegistry("${env.DOCKER_REPOSITORY_URL}", 'docker-repository') {
                    def image = docker.build("${config.imageName}:${BRANCH_NAME}")
                }
            }
        }

        //Do not push images for PR builds
        if (!isPullRequest()) {
            stage("Push ${config.imageName} to Registry") {
                docker.withServer("${env.DOCKER_HOST}") {
                    docker.withRegistry("${env.DOCKER_REPOSITORY_URL}", 'docker-repository') {
                        def image = docker.build("${config.imageName}:${BRANCH_NAME}")
                        image.push()
                        if (env.BRANCH_NAME == 'development') {
                            image.push('latest')
                        }
                    }
                }
            }
        }
    }
    
}