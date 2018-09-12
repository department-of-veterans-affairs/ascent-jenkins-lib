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
        config.dockerHost = env.CI_DOCKER_SWARM_MANAGER
    }

    if (config.dockerDomain == null) {
        config.dockerDomain = env.DOCKER_DEV_DOMAIN
    }

    if (config.vaultAddr == null) {
        config.vaultAddr = env.VAULT_ADDR
    }

    stage("Retrieving Docker Certificates") {
      generateCerts {
        dockerHost = config.dockerHost
        dockerDomainName = config.dockerDomain
        vaultCredID = "jenkins-vault"
        vaultAddress = config.vaultAddr
      }
    }

    def dockerCertPath = env.DOCKER_CERT_LOCATION
    //def dockerSSLArgs = "--tlsverify --tlscacert=${dockerCertPath}/ca.crt --tlscert=${dockerCertPath}/docker_swarm.crt --tlskey=${dockerCertPath}/docker_swarm.key"
    def dockerSSLArgs = ""

    stage("Undeploying Stack: ${stackName}") {
        sh "docker ${dockerSSLArgs} --host ${config.dockerHost} stack rm ${stackName}"
    }

}
