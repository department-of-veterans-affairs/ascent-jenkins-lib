def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }

    def tmpDir = pwd(tmp: true)
    def mvnCmd = "mvn -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Ddockerfile.skip=true -DskipITs=true -s ${tmpDir}/settings.xml"

    stage('Configure Maven') {
        def mavenSettings = libraryResource 'com/lucksolutions/maven/settings.xml'
        writeFile file: "${tmpDir}/settings.xml", text: mavenSettings
    }
    

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
                sh "${mvnCmd} clean compile test-compile"
            }
        }

        try {
            stage('Unit Testing') {
                sh "${mvnCmd} -Dmaven.test.failure.ignore=true test"   
            }
        } finally {
            step([$class: 'JUnitResultArchiver', testResults: '**/surefire-reports/*.xml', healthScaleFactor: 1.0, allowEmptyResults: true])
            if (currentBuild.result == 'UNSTABLE') {
                return 
            }
        }

        stage('Package') {
            try {
                sh "${mvnCmd} package"
            } finally {
                publishHTML (target: [
                    allowMissing: true,
                    alwaysLinkToLastBuild: false,
                    keepAll: true,
                    reportDir: 'target/site/jacoco',
                    reportFiles: 'index.html',
                    reportName: "Code Coverage"
                ])
            }
        }

        stage('Code Analysis') {
            //See https://docs.sonarqube.org/display/SONAR/Analysis+Parameters for more info on Sonar analysis configuration

            
            withSonarQubeEnv('CI') {
                if (isPullRequest()) {
                    //Repo parameter needs to be <org>/<repo name>
                    def repoUrlBase = "https://github.com/"
                    def repo = env.CHANGE_URL.substring(env.CHANGE_URL.indexOf(repoUrlBase) + repoUrlBase.length(),env.CHANGE_URL.indexOf("/pull/"))
                    //Use Preview mode for PRs
                    withCredentials([string(credentialsId: 'Github', variable: 'GITHUB_TOKEN')]) {
                        sh "${mvnCmd} -X -Dsonar.analysis.mode=preview -Dsonar.github.pullRequest=${env.CHANGE_ID} -Dsonar.github.oauth=${GITHUB_TOKEN}  -Dsonar.github.repository=${repo} sonar:sonar"
                    }
                } else {
                    sh "${mvnCmd} sonar:sonar"
                }
            }
        }

        //Only run the Sonar quality gate and deploy stage for non PR builds
        if (!isPullRequest()) {
            // stage("Quality Gate") {
            //     timeout(time: 15, unit: 'MINUTES') {
            //         def qg = waitForQualityGate()
            //         if (qg.status != 'OK') {
            //             error "Pipeline aborted due to quality gate failure: ${qg.status}"
            //         }
            //     }
            // }

            stage('Deploy to Repository') {
                withCredentials([usernamePassword(credentialsId: 'nexus', usernameVariable: 'DEPLOY_USER', passwordVariable: 'DEPLOY_PASSWORD')]) {
                    sh "${mvnCmd} deploy"
                }
            }
        }
    }
}