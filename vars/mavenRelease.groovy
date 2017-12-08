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
        stage('Check master branch') {
            def url = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
            sh "git fetch --no-tags --progress ${url} +refs/heads/master:refs/remotes/origin/master"

            //Compare to master branch to look for any unmerged changes
            def commitsBehind = sh(returnStdout: true, script: "git rev-list --right-only --count HEAD...remotes/origin/master").trim().toInteger()
            if (commitsBehind > 0) {
                error("Master Branch has changesets not included on this branch. Please merge master into your branch before releaseing.")
            } else {
                echo "Branch is up to date with changesets on master. Proceeding with release..."
            }

            sh "git --version"
            sh "git fetch --no-tags --progress ${url} +refs/heads/${env.BRANCH_NAME}:refs/remotes/origin/${env.BRANCH_NAME}"
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
            sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5:set -DnewVersion=${config.releaseVersion} -DgenerateBackupPoms=false"

            //Update SNAPSHOT dependencies to their release versions if available
            sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5::use-releases"

            //Check for any snapshot versions remaining
            sh "${mvnCmd} validate"

            //Commit changes locally
            sh "git status"
            sh "git commit -a -m \"Releasing version ${config.releaseVersion}\""
        }

        def tag = config.releaseVersion
        stage('Tag Release') {
            //Tag release
            sh "git tag -a ${tag} -m \"Release version ${config.releaseVersion}\""
        }

        stage('Set Next Development Version') {
            //Set the next dev version
            sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5::set -DnewVersion=${config.developmentVersion}  -DgenerateBackupPoms=false"
            //Commit changes locally
            sh "git status"
            sh "git commit -a -m \"Preparing POMs for next development version ${config.developmentVersion}\""
        }

        stage('Push to changes to remote branch') {
            // sh "git branch --list"
            // //makes a new branch from current detached HEAD
            // sh "git branch temp"
            // sh "git checkout temp"
            // //Point the remote branch to the detached HEAD
            // sh "git checkout -B ${BRANCH_NAME} temp"
            // //Delete the temp branch
            // sh "git branch –d temp"
            //Push the branch to the remote
            sh "git push origin ${BRANCH_NAME}"
            //Push the tag to the remote
            sh "git push origin refs/tags/${tag}"
        }

        stage('Checkout Tag') {
            sh "git checkout tags/${tag}"
        }

        //Return the Git tag for the release
        return tag
    }
}