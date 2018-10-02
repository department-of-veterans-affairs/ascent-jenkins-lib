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
        def urlMinusProtocol = ''
        withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            stage('Check master branch') {
                url = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
                urlMinusProtocol = url.substring(url.indexOf('://')+3)
                sh "git fetch --no-tags --progress https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} +refs/heads/master:refs/remotes/origin/master"

                //Compare to master branch to look for any unmerged changes
                def commitsBehind = sh(returnStdout: true, script: "git rev-list --right-only --count HEAD...remotes/origin/master").trim().toInteger()
                if (commitsBehind > 0) {
                    error("Master Branch has changesets not included on this branch. Please merge master into your branch before releaseing.")
                } else {
                    echo "Branch is up to date with changesets on master. Proceeding with release..."
                }

                sh "git fetch https://${GIT_USERNAME}:${GIT_PASSWORD}@${urlMinusProtocol} +refs/heads/${env.BRANCH_NAME}:refs/remotes/origin/${env.BRANCH_NAME}"
                sh "git checkout ${env.BRANCH_NAME}"
            }
        }

        def tag = config.releaseVersion
        stage('Tag Release') {
            //Tag release
            sh "git tag -a ${tag} -m \"Release version ${config.releaseVersion}\""
        }

        stage('Push to changes to remote branch') {
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