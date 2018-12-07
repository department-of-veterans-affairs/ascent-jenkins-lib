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
    if (config.serviceProtocol == null) {
        config.serviceProtocol = 'http'
    }
    if (config.vaultAddr == null) {
        config.vaultAddr = env.VAULT_ADDR
    }
    if (config.vaultCredID == null) {
        config.vaultCredID = "jenkins-vault"
    }

    //Setup Maven command line options
    def opts = "-Ddomain=${config.serviceHost} -Dport=${config.servicePort} -Dprotocol=${config.serviceProtocol} -Djavax.net.ssl.keyStore=${config.keystore} -Djavax.net.ssl.keyStorePassword=${config.keystorePassword} -Djavax.net.ssl.trustStore=/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
    if (config.options != null) {
        opts = opts + " ${config.options}"
    }

    def tmpDir = pwd(tmp: true)

    if (config.mavenSettings == null) {
        config.mavenSettings = "${tmpDir}/settings.xml"
        stage('Configure Maven') {
            def mavenSettings = libraryResource 'gov/va/maven/settings.xml'
            writeFile file: config.mavenSettings, text: mavenSettings
        }
    }
    def mvnCmd = "mvn -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dmaven.wagon.http.ssl.allowall=true -Ddockerfile.skip=true -DskipITs=true -s ${config.mavenSettings}"

    dir("${config.directory}") {

        def deployEnv = []
        if (config.testVaultTokenRole != null) {
            stage('Request Vault Token for testing') {
                    withCredentials([string(credentialsId: "${config.vaultCredID}", variable: 'JENKINS_VAULT_TOKEN')]) {
                    vaultToken = sh(returnStdout: true, script: "curl -k -s --header \"X-Vault-Token: ${JENKINS_VAULT_TOKEN}\" --request POST --data '{\"display_name\": \"testenv\"}' ${config.vaultAddr}/v1/auth/token/create/${config.testVaultTokenRole}?ttl=30m | jq '.auth.client_token'").trim().replaceAll('"', '')
                    deployEnv.add("VAULT_TOKEN=${vaultToken}")
                }
            }
        }


        try {
            stage("Performance Testing") {
                echo "Executing performance tests against ${config.serviceHost}"
                withEnv(deployEnv) {
                    withCredentials([usernamePassword(credentialsId: 'nexus', usernameVariable: 'DEPLOY_USER', passwordVariable: 'DEPLOY_PASSWORD')]) {
                        sh "${mvnCmd} -Pperftest verify ${opts} "
                    }
                }
            }
        } finally {
            //If performance test results exist, then publish those to Jenkins
            //get list of all report directories
            def reportDirs = sh(returnStdout: true, script: "ls -d -1 */target/jmeter/reports/*").split( "\\r?\\n" )
            echo "Report Directorys are: ${reportDirs}"
            for (directory in reportDirs) {
                //truncate at first under-score to get report name
                def reportName = directory.substring(directory.lastIndexOf('/')+1, directory.length())

                publishHTML (target: [
                    allowMissing: false,
                    alwaysLinkToLastBuild: false,
                    keepAll: true,
                    reportDir: "${directory}",
                    reportFiles: 'index.html',
                    reportName: "${reportName}"
                ])
            }

            //Publish Jmeter logs for debugging failing tests
            archiveArtifacts(
                artifacts: "**/*.jmx.log",
                allowEmptyArchive: true
            )
        }
    }
}
