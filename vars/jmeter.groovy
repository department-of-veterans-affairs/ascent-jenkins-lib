def call(body) {

    def config = [:]
    def vaultToken = null
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    
    if (config.directory == null) {
        config.directory = '.'
    }
    if (config.serviceHost == null) {
        error "serviceHost parameters must be specified"
    }
    if (config.servicePort == null) {
        error "servicePort parameters must be specified"
    }
    if (config.testPlan == null) {
        error "testPlan parameter must be specified"
    }
    if (config.jmeterReportDirectory == null) {
        config.jmeterReportDirectory = 'target/jmeterReports'
    }
    if (config.serviceProtocol == null) {
        config.serviceProtocol = 'http'
    }

    //Setup JMeter command line options
    def jmeterOpts = "-Jbaseurl=${config.serviceHost} -Jport=${config.servicePort}"
    if (config.threads != null) {
        jmeterOpts = jmeterOpts + " -Jloadusers=${config.threads}"
    }
    if (config.duration != null) {
        jmeterOpts = jmeterOpts + " -Jlduration=${config.duration}"
    }

    dir("${config.directory}") {

        def deployEnv = []
        if (config.testVaultTokenRole != null) {
            stage('Request Vault Token for testing') {
                    withCredentials([string(credentialsId: 'jenkins-vault', variable: 'JENKINS_VAULT_TOKEN')]) {
                    vaultToken = sh(returnStdout: true, script: "curl -k -s --header \"X-Vault-Token: ${JENKINS_VAULT_TOKEN}\" --request POST --data '{\"display_name\": \"testenv\"}' ${env.VAULT_ADDR}/v1/auth/token/create/${config.testVaultTokenRole}?ttl=30m | jq '.auth.client_token'").trim().replaceAll('"', '')
                    deployEnv.add("VAULT_TOKEN=${vaultToken}")
                }
            } 
        }

        for (plan in config.testPlan) {
            def filename = plan.substring(plan.lastIndexOf('/'),plan.lastIndexOf('.'))
            def logFile = "target/jmeterLogs/${filename}.csv"
            def reportDir = "${config.jmeterReportDirectory}/${filename}"
            try {
                stage("Performance Testing - ${plan}") {
                    echo "Executing performance tests against ${config.serviceHost}"
                    withEnv(deployEnv) {
                        sh "mkdir -P ${reportDir}"
                        sh "jmeter -n -t ${plan} -l ${logFile} -e -o ${reportDir} ${jmeterOpts}"
                    }
                }
            } finally {
                //If performance test results exist, then publish those to Jenkins
                if (fileExists("${logFile}")) {
                    performanceReport parsers: [[$class: 'JMeterParser', glob: "${logFile}"]],
                        relativeFailedThresholdNegative: 1.2,
                        relativeFailedThresholdPositive: 1.89,
                        relativeUnstableThresholdNegative: 1.8,
                        relativeUnstableThresholdPositive: 1.5
                }
            }
        }
    }
}