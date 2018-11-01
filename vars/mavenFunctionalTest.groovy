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
    if (config.vaultAddr == null) {
        config.vaultAddr = env.VAULT_ADDR
    }
    if (config.vaultCredID == null) {
        config.vaultCredID = "jenkins-vault"
    }
    if (config.cucumberReportDirectory == null) {
        config.cucumberReportDirectory = 'target'
    }
    if (config.cucumberOpts == null) {
        config.cucumberOpts = ''
    }
    if (config.options == null) {
        config.options = ''
    }

    def tmpDir = pwd(tmp: true)


    if (config.mavenSettings == null) {
        config.mavenSettings = "${tmpDir}/settings.xml"
        stage('Configure Maven') {
            def mavenSettings = libraryResource 'gov/va/maven/settings.xml'
            writeFile file: config.mavenSettings, text: mavenSettings
        }
    }

    def mvnCmd = "mvn -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Ddockerfile.skip=true -s ${config.mavenSettings}"

    dir("${config.directory}") {

        try {
            def deployEnv = []
            if (config.testVaultTokenRole != null) {
                stage('Request Vault Token for testing') {
                     withCredentials([string(credentialsId: "${config.vaultCredID}", variable: 'JENKINS_VAULT_TOKEN')]) {
                        vaultToken = sh(returnStdout: true, script: "curl -k -s --header \"X-Vault-Token: ${JENKINS_VAULT_TOKEN}\" --request POST --data '{\"display_name\": \"testenv\"}' ${config.vaultAddr}/v1/auth/token/create/${config.testVaultTokenRole}?ttl=30m | jq '.auth.client_token'").trim().replaceAll('"', '')
                        deployEnv.add("VAULT_TOKEN=${vaultToken}")
                    }
                }
            }

            stage('Functional Testing') {
                echo "Executing functional tests against ${config.serviceUrl}"
                withEnv(deployEnv) {
                    sh "${mvnCmd} integration-test -P inttest -Dbrowser=HtmlUnit -Dtest.env=dev -DbaseURL=${config.serviceUrl} -Djavax.net.ssl.keyStore=${config.keystore} -Djavax.net.ssl.keyStorePassword=${config.keystorePassword} -Djavax.net.ssl.trustStore=/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/cacerts -Djavax.net.ssl.trustStorePassword=changeit -DX-Vault-Token=${vaultToken} -Dvault.url.domain='${config.vaultAddr}' -Dcucumber.options='${config.cucumberOpts}' ${config.options}"
                }
            }
        } finally {
            if (fileExists("${config.cucumberReportDirectory}/cucumber.json")) {
                step([$class: 'CucumberReportPublisher',
                    jenkinsBasePath: "${JENKINS_URL}",
                    fileIncludePattern: '**/cucumber.json',
                    fileExcludePattern: '',
                    jsonReportDirectory: "${config.cucumberReportDirectory}",
                    parallelTesting: false,
                    buildStatus: 'UNSTABLE'])
                if (currentBuild.result == 'UNSTABLE') {
                    return
                }
            }
        }
    }
}
