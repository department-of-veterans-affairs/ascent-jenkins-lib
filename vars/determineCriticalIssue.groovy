def call(criticalText) {
  // -- Check if there are critical issue in the file
  if(isCriticalIssue("${criticalText}")) {
    print "THERE ARE CRITICAL ISSUES!!!!!!!"
  }
}
