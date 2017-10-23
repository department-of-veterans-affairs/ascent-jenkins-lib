def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }

    dir("${config.directory}") {
        withCredentials([usernamePassword(credentialsId: 'aws', usernameVariable: 'AWS_ACCESS_KEY', passwordVariable: 'AWS_SECRET_KEY')]) {
            stage("Build") {
                try {

                    def variables = "-var aws_access_key=${env.AWS_ACCESS_KEY} -var aws_secret_key=${env.AWS_SECRET_KEY} "
                    def keys = config.vars.keySet();
                    for (int i=0; i<keys.size(); ++i) {
                        def key = keys[i]
                        variables = "${variables} -var ${key}=${config.vars[key]} "
                    }

                    sh "packer build ${variables} ${config.packerFile}"
                } catch (e) {
                    currentBuild.result = "FAILED"
                    throw e
                }
            }
        }
    }
}