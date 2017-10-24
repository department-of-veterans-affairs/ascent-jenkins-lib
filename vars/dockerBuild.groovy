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

        //If there is a pom file in this project then use its version, otherwise default to the branch name
        if (config.version == null) {
            if (fileExists('pom.xml')) {
                pom = readMavenPom file: 'pom.xml'
                config.version = pom.version
            } else {
                config.version = env.BRANCH_NAME
            }
        }

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
                        def image = docker.build("${config.imageName}:${config.version}")
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