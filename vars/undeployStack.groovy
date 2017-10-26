/*
* Deploys a stack of Docker containers to the given Docker Swarm
*
*
*
*/
def call(body) {

    def config = [:]
    def stackName = stackName()
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.dockerHost == null) {
        dockerHost = env.DOCKER_SWARM_MANAGER
    }

    stage("Undeploying Stack: ${stackName}") {
        sh "docker --host ${config.dockerHost} stack rm ${stackName}"
    }
    
}