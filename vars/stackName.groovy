//Return a unique stack name for deployments
def call() {
    return (env.JOB_NAME + '_' + env.BUILD_NUMBER).replaceAll('/', '_').replaceAll('^department-of-veterans-affairs_', '')
}