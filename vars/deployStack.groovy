/*
* Deploys a stack of Docker containers to the given Docker Swarm
*
*
*
*/
def call(body) {

    def config = [:]
    def vaultToken = null
    def dockerFiles = ""
    def publishedPort = 8762
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

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
    if (config.vaultCredID == null) {
        config.vaultCredID = "jenkins-vault"
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
    if (config.stackName == null) {
        config.stackName = stackName()
    }
    if (config.vaultTokens == null) {
        config.vaultTokens = [:]
    }
    if (config.networkName == null) {
        config.networkName = 'ascentnet'
    }
    if(config.keystoreAlias == null) {
        config.keystoreAlias = config.dockerDomain
    }
    if(config.certFileName == null) {
      config.certFileName = config.keystoreAlias
    }

    for (file in config.composeFiles) {
        if (fileExists(file)) {
            dockerFiles = dockerFiles + "-c " + file + " "
        } else {
            error(file + 'was not found')
        }
    }

    def dockerCertPath = env.DOCKER_CERT_LOCATION
    def dockerSSLArgs = "--tlsverify --tlscacert=${dockerCertPath}/${config.certFileName}_ca.crt --tlscert=${dockerCertPath}/${config.certFileName}.crt --tlskey=${dockerCertPath}/${config.certFileName}.key"


    def deployEnv = ["DRIVER_TYPE=overlay", "VAULT_SCHEME=https"]
    if (config.deployEnv != null) {
        deployEnv.addAll(config.deployEnv)
    }
    echo "Configured Port is: ${config.port}"
    if (config.port != null) {
        echo "Adding port to env..."
        deployEnv.add("PORT=${config.port}")
    }

    deployEnv.add("VAULT_ADDR=${config.vaultAddr}")

    echo "Deploy Env is: ${deployEnv}"

    stage("Requesting Vault Token for application") {
        withCredentials([string(credentialsId: "${config.vaultCredID}", variable: 'JENKINS_VAULT_TOKEN')]) {
            for (x in config.vaultTokens.keySet()) {
                def var = x
                vaultToken = sh(returnStdout: true, script: "curl -k -s --header \"X-Vault-Token: ${JENKINS_VAULT_TOKEN}\" --request POST --data '{\"display_name\": \"testenv\"}' ${config.vaultAddr}/v1/auth/token/create/${config.vaultTokens[var]}?ttl=${config.tokenTTL} | jq '.auth.client_token'").trim().replaceAll('"', '')
                deployEnv.add("${var}=${vaultToken}")
            }
        }
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

    //Check to see if our networks are in place. If not, create them before deploying the stack.
    withEnv(deployEnv) {
        def networkId = sh(returnStdout: true, script: "docker ${dockerSSLArgs} --host ${config.dockerHost} network ls -f label=gov.va.ascent.network=${config.networkName} -q")
        if (networkId == null || networkId.isAllWhitespace()) {
            stage("Creating Network: ${config.networkName}") {
                sh "docker ${dockerSSLArgs} --host ${config.dockerHost} network create -d overlay --label gov.va.ascent.network=${config.networkName} ${config.networkName}"
            }
        }
    }

    stage("Deploying Stack: ${config.stackName}") {
        withEnv(deployEnv) {
            sh "docker ${dockerSSLArgs} --host ${config.dockerHost} stack deploy ${dockerFiles} ${config.stackName}"
        }

        //Query docker every minute to see if deployment is complete
        //echo 'Wating for containers to finish deploying...'
        // timeout(time: 10, unit: 'MINUTES') {
        //     def deployDone = false
        //     waitUntil {
        //         sleep(30)
        //         sh(script: "docker --host ${config.dockerHost} stack ps ${config.stackName} --format {{.CurrentState}}")
        //         def result = sh(returnStdout: true, script: "docker --host ${config.dockerHost} stack ps ${config.stackName} --format {{.CurrentState}}")
        //         deployDone = !(result.contains('Failed') || result.contains('Preparing') || result.contains('Starting'))
        //         echo "Deployment is done: ${deployDone}"
        //         return deployDone;
        //     }
        // }

        echo 'Sleep for a few minutes and cross our fingers that the services started. Need to find a more reliable way of checking container health.'
        sleep(config.deployWaitTime)
        sh "docker ${dockerSSLArgs} --host ${config.dockerHost} stack ps ${config.stackName} --no-trunc"
        sh "docker ${dockerSSLArgs} --host ${config.dockerHost} stack services ${config.stackName}"
        echo 'Containers are successfully deployed'

        if (config.serviceName != null) {
            try {
                def service = "${config.stackName}_${config.serviceName}"
                publishedPort = sh(returnStdout: true, script: "docker ${dockerSSLArgs} --host ${config.dockerHost} service inspect ${service} --format '{{range \$p, \$conf := .Endpoint.Ports}} {{(\$conf).PublishedPort}} {{end}}'").trim()
            } catch (ex) {
                echo "Didn't find ${config.serviceName} in stack. Returning default gateway port 8761."
                publishedPort = 8762
            }
        }
    }

    return publishedPort

}
