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
        if (config.version == null || config.version == '') {
            if (fileExists('pom.xml')) {
                pom = readMavenPom(file: 'pom.xml')
                config.version = pom.version ?: env.BRANCH_NAME
            } else {
                config.version = env.BRANCH_NAME
            }
        }

        def image = null
        // try {
            stage("Build ${config.imageName}") {
                docker.withServer("${env.DOCKER_HOST}") {
                    docker.withRegistry("${env.DOCKER_REPOSITORY_URL}", 'docker-repository') {
                        image = docker.build("${config.imageName}:${config.version}")
                    }
                }
            }

            //Do not push images for PR builds
            if (!isPullRequest()) {
                stage("Push ${config.imageName} to Registry") {
                    docker.withServer("${env.DOCKER_HOST}") {
                        //Push to Docker Hub
                        docker.withRegistry("${env.DOCKER_REPOSITORY_URL}", 'docker-repository') {
                            image.push()
                            if (env.BRANCH_NAME == 'development') {
                                image.push('latest')
                            }
                        }
                        //Push to Nexus
                        docker.withRegistry("${env.NEXUS_REPOSITORY_URL}", 'nexus') {
                            image.push()
                            if (env.BRANCH_NAME == 'development') {
                                image.push('latest')
                            }
                        }
                    }
                }
            }

            stage("Remove ${config.imageName}") {
                try {
                    echo "Removing ${image.id}"
                    sh "docker -H ${env.DOCKER_HOST} rmi --force \$(docker -H ${env.DOCKER_HOST} images ${image.id} -q)"
                } catch (ex) {
                    //Its OK if it can't clean up an image. Sometime duplicate ids are returned and we
                    //don't want to fail the pipeline because of this.
                }
            }
    }
    
}