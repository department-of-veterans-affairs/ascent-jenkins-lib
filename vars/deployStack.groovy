/*
* Deploys a stack of Docker containers to the given Docker Swarm
*
*
*
*/
def call(body) {

    def config = [:]
    def vaultToken = null
    def stackName = stackName()
    def dockerFiles = ""
    def publishedPort = 8762
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def dockerCertPath = env.DOCKER_CERT_LOCATION
    //def dockerSSLArgs = "--tlsverify --tlscacert=${dockerCertPath}/ca.crt --tlscert=${dockerCertPath}/docker_swarm.crt --tlskey=${dockerCertPath}/docker_swarm.key"
    def dockerSSLArgs = ""

    if (config.composeFiles == null) {
        error('No compose files defined for deployment')
    }
    if (config.dockerHost == null) {
        config.dockerHost = env.CI_DOCKER_SWARM_MANAGER
    }

    if (config.dockerDomain == null) {
        config.dockerDomain = env.DOCKER_DEV_DOMAIN
    }

    if (config.vaultAddr == null) {
        config.vaultAddr = env.VAULT_ADDR
    }

    if (config.vaultRole == null) {
        config.vaultRole = 'ascent-platform'
    }
    if (config.deployWaitTime == null) {
        config.deployWaitTime = 300
    }
    if (config.tokenTTL == null) {
        config.tokenTTL = '30m'
    }

    for (file in config.composeFiles) {
        if (fileExists(file)) {
            dockerFiles = dockerFiles + "-c " + file + " "
        } else {
            error(file + 'was not found')
        }
    }

    def deployEnv = []
    if (config.deployEnv != null) {
        deployEnv.plus(config.deployEnv)
    }

    stage("Requesting Vault Token for application") {
        withCredentials([string(credentialsId: 'jenkins-vault', variable: 'JENKINS_VAULT_TOKEN')]) {
            for (x in config.vaultTokens.keySet()) {
                def var = x
                vaultToken = sh(returnStdout: true, script: "curl -k -s --header \"X-Vault-Token: ${JENKINS_VAULT_TOKEN}\" --request POST --data '{\"display_name\": \"testenv\"}' ${env.VAULT_ADDR}/v1/auth/token/create/${config.vaultTokens[var]}?ttl=${config.tokenTTL} | jq '.auth.client_token'").trim().replaceAll('"', '')
                deployEnv.add("${var}=${vaultToken}")
            }
        }
    }

    stage("Retrieving Docker Certificates") {
      generateCerts {
        dockerHost = config.dockerHost
        dockerDomainName = config.dockerDomain
        vaultCredID = "jenkins-vault"
        vaultAddress = config.vaultAddr
      }
    }

    stage("Deploying Stack: ${stackName}") {
        withEnv(deployEnv) {
            sh "docker ${dockerSSLArgs} --host ${config.dockerHost} stack deploy ${dockerFiles} ${stackName}"
        }

        //Query docker every minute to see if deployment is complete
        //echo 'Wating for containers to finish deploying...'
        // timeout(time: 10, unit: 'MINUTES') {
        //     def deployDone = false
        //     waitUntil {
        //         sleep(30)
        //         sh(script: "docker --host ${config.dockerHost} stack ps ${stackName} --format {{.CurrentState}}")
        //         def result = sh(returnStdout: true, script: "docker --host ${config.dockerHost} stack ps ${stackName} --format {{.CurrentState}}")
        //         deployDone = !(result.contains('Failed') || result.contains('Preparing') || result.contains('Starting'))
        //         echo "Deployment is done: ${deployDone}"
        //         return deployDone;
        //     }
        // }

        echo 'Sleep for a few minutes and cross our fingers that the services started. Need to find a more reliable way of checking container health.'
        sleep(config.deployWaitTime)
        sh "docker ${dockerSSLArgs} --host ${config.dockerHost} stack ps ${stackName} --no-trunc"
        echo 'Containers are successfully deployed'

        if (config.serviceName != null) {
            def service = "${stackName}_${config.serviceName}"
            publishedPort = sh(returnStdout: true, script: "docker ${dockerSSLArgs} --host ${config.dockerHost} service inspect ${service} --format '{{range \$p, \$conf := .Endpoint.Ports}} {{(\$conf).PublishedPort}} {{end}}'").trim()
        }
    }

    return publishedPort

}
