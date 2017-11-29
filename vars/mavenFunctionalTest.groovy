def call(body) {

    def config = [:]
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
            stage('Functional Testing') {
                echo "Executing functional tests against ${config.serviceUrl}"
                sh "${mvnCmd} integration-test -P inttest -DbaseURL=${config.serviceUrl}"   
            }
        } finally {
            step([$class: 'CucumberReportPublisher',
                jenkinsBasePath: 'http://jenkins.internal.vets-api.gov:8080/',
                fileIncludePattern: '**/cucumber.json',
                fileExcludePattern: '',
                jsonReportDirectory: "${config.cucumberReportDirectory}",
                ignoreFailedTests: false, missingFails: false, pendingFails: false, skippedFails: false, undefinedFails: false, parallelTesting: false])
            if (currentBuild.result == 'UNSTABLE') {
                return
            }
        }
    }
}