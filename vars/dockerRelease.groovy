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
        //Create a milestone that will abort older builds when a newer build passes this stage.
        if (config.releaseVersion == null) {
            milestone()
            def versions = input(id: "versions", message: "Release this build?", parameters: [
                [$class: 'StringParameterDefinition', description: 'Release Version', name: 'release']
            ])
            config.releaseVersion = versions['release']
            milestone()
        }

        node {
            dir("${config.directory}") {
                stage('Build Info') {
                    echo "Branch Name: ${env.BRANCH_NAME}"
                    echo "Change ID: ${env.CHANGE_ID}"
                    echo "Change URL: ${env.CHANGE_URL}"
                    echo "Change Target: ${env.CHANGE_TARGET}"
                    echo "ChangeSet Size: ${currentBuild.changeSets.size()}"
                    echo "Pull Request?: ${isPullRequest()}"
                    echo ("Release Version: "+config.releaseVersion)
                }

                stage('Checkout SCM') {
                    checkout scm
                }

                dockerBuild {
                    directory = config.directory
                    imageName = config.imageName
                    version = config.releaseVersion
                }
            }
        }
    }
}