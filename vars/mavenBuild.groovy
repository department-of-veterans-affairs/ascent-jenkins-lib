def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }
    if (config.skipFortify == null) {
        config.skipFortify = false
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

        stage('Debug') {
            echo "Branch Name: ${env.BRANCH_NAME}"
            echo "Change ID: ${env.CHANGE_ID}"
            echo "Change URL: ${env.CHANGE_URL}"
            echo "Change Target: ${env.CHANGE_TARGET}"
            echo "ChangeSet Size: ${currentBuild.changeSets.size()}"
            echo "Pull Request?: ${isPullRequest()}"
        }

        stage('Maven Build') {
            withCredentials([usernamePassword(credentialsId: 'nexus', usernameVariable: 'DEPLOY_USER', passwordVariable: 'DEPLOY_PASSWORD')]) {
                sh "${mvnCmd} -U clean compile test-compile"
            }
        }

        try {
            stage('Unit Testing') {
                sh "${mvnCmd} -Dmaven.test.failure.ignore=true package"
            }
        } finally {
            step([$class: 'JUnitResultArchiver', testResults: '**/surefire-reports/*.xml', healthScaleFactor: 1.0, allowEmptyResults: true])
            if (currentBuild.result == 'UNSTABLE') {
                return
            }
        }

        if (!config.skipFortify) {
            fortifyStage {
                directory = config.directory
                failOnGates = false
            }

        stage('Code Analysis') {
            //See https://docs.sonarqube.org/display/SONAR/Analysis+Parameters for more info on Sonar analysis configuration


            withSonarQubeEnv('CI') {
                if (isPullRequest()) {
                    //Repo parameter needs to be <org>/<repo name>
                    def repoUrlBase = "https://github.com/"
                    def repo = env.CHANGE_URL.substring(env.CHANGE_URL.indexOf(repoUrlBase) + repoUrlBase.length(),env.CHANGE_URL.indexOf("/pull/"))
                    //Use Preview mode for PRs
                    withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
                        sh "${mvnCmd} -X -Dsonar.analysis.mode=preview -Dsonar.github.pullRequest=${env.CHANGE_ID} -Dsonar.github.oauth=${GITHUB_TOKEN}  -Dsonar.github.repository=${repo} sonar:sonar"
                    }
                } else {
                    sh "${mvnCmd} sonar:sonar"
                }
            }

        }



        //Only run the Sonar quality gate and deploy stage for non PR builds
        if (!isPullRequest()) {
            stage("Quality Gate") {
                timeout(time: 15, unit: 'MINUTES') {
                    def qg = waitForQualityGate()
                    if (qg.status != 'OK') {
                        error "Pipeline aborted due to quality gate failure: ${qg.status}"
                    }
                }
            }
            stage('Deploy to Repository') {
                withCredentials([usernamePassword(credentialsId: 'nexus', usernameVariable: 'DEPLOY_USER', passwordVariable: 'DEPLOY_PASSWORD')]) {
                    sh "${mvnCmd} deploy"
                }
            }
        }
    }
}
