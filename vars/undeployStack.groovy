/*
* Deploys a stack of Docker containers to the given Docker Swarm
*
*
*
*/
def call(body) {

    def config = [:]
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

    if (config.vaultCredID == null) {
        config.vaultCredID = "jenkins-vault"
    }
    if(config.keystoreAlias == null) {
        config.keystoreAlias = config.dockerDomain
    }

    if (config.stackName == null) {
        config.stackName = stackName()
    }

    if (config.certFileName == null) {
      currentBuild.result = 'ABORTED'
      error('Aborting pipeline cert file name not provided')
    }

    stage("Retrieving Docker Certificates") {
      generateCerts {
        dockerHost = config.dockerHost
        dockerDomainName = config.dockerDomain
        vaultCredID = config.vaultCredID
        vaultAddress = config.vaultAddr
        keystoreAlias = config.keystoreAlias
      }
    }

    def dockerCertPath = env.DOCKER_CERT_LOCATION
    def dockerSSLArgs = "--tlsverify --tlscacert=${dockerCertPath}/${config.certFileName}_ca.crt --tlscert=${dockerCertPath}/${config.certFileName}.crt --tlskey=${dockerCertPath}/${config.certFileName}.key"

    stage("Undeploying Stack: ${config.stackName}") {
        def stackExists = sh(returnStdout: true, script: "docker ${dockerSSLArgs} --host ${config.dockerHost} stack ls | grep ${config.stackName}")
        if (stackExists?.trim()) {
            sh "docker ${dockerSSLArgs} --host ${config.dockerHost} stack rm ${config.stackName}"
        }
    }

}
