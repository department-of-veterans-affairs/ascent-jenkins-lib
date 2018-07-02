def call(criticalText) {
  // -- Check if there are critical issue in the file
  if(isCriticalIssue("${criticalText}")) {
    // TODO: uncomment this once we have suppressions for the rules loaded
    //error "THERE ARE CRITICAL ISSUES!!!!!!!"
    
    // For now, use this
    print "THERE ARE CRITICAL ISSUES!!!!!!"
  }
}
