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
    if (config.cucumberReportDirectory == null) {
        config.cucumberReportDirectory = 'target'
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
                     withCredentials([string(credentialsId: 'jenkins-vault', variable: 'JENKINS_VAULT_TOKEN')]) {
                        vaultToken = sh(returnStdout: true, script: "curl -k -s --header \"X-Vault-Token: ${JENKINS_VAULT_TOKEN}\" --request POST --data '{\"display_name\": \"testenv\"}' ${env.VAULT_ADDR}/v1/auth/token/create/${config.testVaultTokenRole}?ttl=30m | jq '.auth.client_token'").trim().replaceAll('"', '')
                        deployEnv.add("VAULT_TOKEN=${vaultToken}")
                    }
                } 
            }

            stage('Functional Testing') {
                echo "Executing functional tests against ${config.serviceUrl}"
                withEnv(deployEnv) {
                    sh "${mvnCmd} integration-test -P inttest -Dbrowser=HtmlUnit -Dtest.env=ci -DbaseURL=${config.serviceUrl} -DX-Vault-Token=${env.VAULT_TOKEN}"
                }
            }
        } finally {
            if (fileExists("${config.cucumberReportDirectory}/cucumber.json")) {
                step([$class: 'CucumberReportPublisher',
                    jenkinsBasePath: 'http://jenkins.internal.vets-api.gov:8080/',
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