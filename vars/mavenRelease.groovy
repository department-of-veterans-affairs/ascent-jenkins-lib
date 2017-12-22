def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    //Optional Parameters
    if (config.directory == null) {
        config.directory = '.'
    }

    if (!isPullRequest() && currentBuild.result == null) {
        def url = ''
        stage('Check master branch') {
           url = sh(returnStdout: true, script: 'git config remote.origin.url').trim()

            sh "git fetch origin"
            sh "git checkout ${env.BRANCH_NAME}"
        }

        def tmpDir = pwd(tmp: true)
        if (config.mavenSettings == null) {
            config.mavenSettings = "${tmpDir}/settings.xml"
            stage('Configure Maven') {
                def mavenSettings = libraryResource 'gov/va/maven/settings.xml'
                writeFile file: config.mavenSettings, text: mavenSettings
            }
        }
        def mvnCmd = "mvn -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Ddockerfile.skip=true -DskipITs=true -s ${config.mavenSettings}"

        stage('Set Release Version') {
            //Set release version
            sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5:set -DnewVersion=${config.releaseVersion} -DgenerateBackupPoms=false -DprocessAllModules=true"

            //Update SNAPSHOT dependencies to their release versions if available
            sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5::use-releases -DprocessParent=true"

            //Check for any snapshot versions remaining
            sh "${mvnCmd} compile validate"

            //Commit changes locally
            sh "git commit -a -m \"Releasing version ${config.releaseVersion}\""
        }

        def tag = config.releaseVersion
        stage('Tag Release') {
            //Tag release
            sh "git tag -a ${tag} -m \"Release version ${config.releaseVersion}\""
        }

        stage('Set Next Development Version') {
            //Set the next dev version
            sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5::set -DnewVersion=${config.developmentVersion}  -DgenerateBackupPoms=false -DprocessAllModules=true"
            //Commit changes locally
            sh "git commit -a -m \"Preparing POMs for next development version ${config.developmentVersion}\""
        }

        stage('Push to changes to remote branch') {
            def urlMinusProtocol = url.substring(url.indexOf('://')+3)
            withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                //Push the branch to the remote
                sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} ${BRANCH_NAME}"
                //Push the tag to the remote
                sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} --tags"
            }
        }

        stage('Checkout Tag') {
            sh "git checkout tags/${tag}"
        }

        //Return the Git tag for the release
        return tag
    }
}