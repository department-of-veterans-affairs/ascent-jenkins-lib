//If this was not a manually triggered build, there were no SCM changes, and this is not the first build of the project:
//Then there is no need to run the build
def call() {

    //Check to see if its the first build
    def causes = currentBuild.rawBuild.getCauses()
    def manualBuild = false;
    for (int i=0; i<causes.size(); ++i) {
        if (causes[i].getShortDescription().startsWith('Started by user')) {
            manualBuild = true;
        }
    }

    if (currentBuild.getPreviousBuild() != null && !manualBuild && currentBuild.changeSets.size() == 0) {
            currentBuild.result = 'ABORTED'
            error('Aborting pipeline due to new actual changes since last build...')
    }
}