def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    //Optional Parameters
    if (config.directory == null) {
        config.directory = '.'
    }

    def buildStatus =  currentBuild.result ?: 'SUCCESSFUL'
    echo "Build Status: ${currentBuild.result}" 

    if (!isPullRequest() && buildStatus == 'SUCCESSFUL') {
        //Create a milestone that will abort older builds when a newer build passes this stage.
        milestone()
        def versions = input(id: "versions", message: "Release this build?", parameters: [
            [$class: 'StringParameterDefinition', defaultValue: '', description: 'Release Version', name: 'release'],
            [$class: 'StringParameterDefinition', defaultValue: '', description: 'Next Development Version', name: 'development']
        ])
        milestone()

        node {
            stage('Build Info') {
                echo "Branch Name: ${env.BRANCH_NAME}"
                echo "Change ID: ${env.CHANGE_ID}"
                echo "Change URL: ${env.CHANGE_URL}"
                echo "Change Target: ${env.CHANGE_TARGET}"
                echo "ChangeSet Size: ${currentBuild.changeSets.size()}"
                echo "Pull Request?: ${isPullRequest()}"
                echo ("Release Version: "+versions['release'])
                echo ("Next Development Version: "+versions['development'])
            }

            stage('Checkout SCM') {
                checkout scm
            }

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
            }

            def tmpDir = pwd(tmp: true)
            def mvnCmd = "mvn -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Ddockerfile.skip=true -DskipITs=true -s ${tmpDir}/settings.xml"

            stage('Configure Maven') {
                def mavenSettings = libraryResource 'com/lucksolutions/maven/settings.xml'
                writeFile file: "${tmpDir}/settings.xml", text: mavenSettings
            }

            stage('Set Release Version') {
                //Set release version
                sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5:set -DnewVersion=${versions['release']}"

                //Update SNAPSHOT dependencies to their release versions if available
                sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5::use-releases"

                //Check for any snapshot versions remaining
                //sh "${mvnCmd} enforcer:enforce -Denforcer.fail=true"
            }

            stage('Tag Release') {
                //Tag release
                sh "git tag -a ${versions['release']} -m \"Release version ${versions['release']}\""
                //Commit changes locally
                sh "git commit -a -m \"Releasing version ${versions['release']}\""
            }

            //Build the new release
            mavenBuild {
                directory = config.directory
            }

            stage('Set Next Development Version') {
                //Set the next dev version
                sh "${mvnCmd} org.codehaus.mojo:versions-maven-plugin:2.5::set -DnewVersion=${versions['development']}"
                //Commit changes locally
                sh "git commit -a -m \"Preparing POMs for next development version ${versions['development']}\""

                //Push to Github
                sh "git status"
            }
        }
    }
}