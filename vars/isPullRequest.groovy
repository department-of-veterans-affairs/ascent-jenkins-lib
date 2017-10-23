//Return true if this is a build of a Pull Request
def call() {

    return (env.CHANGE_URL != null && env.CHANGE_URL.endsWith("/pull/${env.CHANGE_ID}"))
}