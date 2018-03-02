def call(body) {

    def config = [:]
    def vaultToken = null
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }
    if (config.serviceUrl == null) {
        error "serviceUrl parameters must be specified"
    }
    if (config.testPlan == null) {
        error "testPlan parameter must be specified"
    }
    if (config.jmeterReportDirectory == null) {
        config.jmeterReportDirectory = 'target/jmeterReports'
    }

    dir("${config.directory}") {

        try {
            def deployEnv = []
            if (config.testVaultTokenRole != null) {
                stage('Request Vault Token for testing') {
                     withCredentials([string(credentialsId: 'jenkins-vault', variable: 'JENKINS_VAULT_TOKEN')]) {
                        vaultToken = sh(returnStdout: true, script: "curl -k -s --header \"X-Vault-Token: ${JENKINS_VAULT_TOKEN}\" --request POST --data '{\"display_name\": \"testenv\"}' ${env.VAULT_ADDR}/v1/auth/token/create/${config.testVaultTokenRole}?ttl=30m | jq '.auth.client_token'").trim().replaceAll('"', '')
                        deployEnv.add("VAULT_TOKEN=${vaultToken}")
                    }
                } 
            }

            stage('Performance Testing Testing') {
                echo "Executing performance tests against ${config.serviceUrl}"
                withEnv(deployEnv) {
                    sh "jmeter -n -t ${config.testPlan} -l ${config.logFile} -e -o ${config.jmeterReportDirectory}"
                }
            }
        } finally {
            //If performance test results exist, then publish those to Jenkins
            if (fileExists("${config.logFile}")) {
                performanceReport parsers: [[$class: 'JMeterParser', glob: "${config.logFile}"]],
                    relativeFailedThresholdNegative: 1.2,
                    relativeFailedThresholdPositive: 1.89,
                    relativeUnstableThresholdNegative: 1.8,
                    relativeUnstableThresholdPositive: 1.5
            }
        }
    }
}