/*
* Deploys a stack of Docker containers to the given Docker Swarm
*
*
*
*/
def call(body) {

    def config = [:]
    def stackName = env.JOB_NAME + '-' + env.BRANCH_NAME + '-' + env.BUILD_NUMBER
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.dockerHost == null) {
        dockerHost = env.DOCKER_SWARM_MANAGER
    }

    stage("Undeploying Stack: ${stackName}") {
        sh "docker stack rm --host ${config.dockerHost} ${stackName}"
    }
    
}