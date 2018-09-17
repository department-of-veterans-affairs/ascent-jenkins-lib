def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if (config.dockerHost == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because the docker host to generate the certificates for is unknown')
  }

  if (config.dockerDomainName == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because a docker domain name was not provided')
  }

  if (config.vaultCredID == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because vault credential ID was not provided')
  }

  if (config.vaultAddress == null) {
    currentBuild.result = 'ABORTED'
    error('Aborting pipeline because vault address was not provided')
  }

  if (config.keystoreAlias == null) {
    config.keystoreAlias = "ascent"
  }


  def DOCKER_IP_ADDRESS = config.dockerHost[6..-6]
  print "ip address: ${DOCKER_IP_ADDRESS}"
  // ------ populate dynamic templates in jenkins slave
  // CA
  sh 'sed -i s?ROLE_NAME?vetservices?g /tmp/templates/ca.crt.tpl'
  sh "sed -i s?DOCKER_HOST_NAME?${config.dockerDomainName}?g /tmp/templates/ca.crt.tpl"
  sh "sed -i s?DOCKER_HOST_IP?${DOCKER_IP_ADDRESS}?g /tmp/templates/ca.crt.tpl"
  // docker_swarm.crt
  sh 'sed -i s?ROLE_NAME?vetservices?g /tmp/templates/docker_swarm.crt.tpl'
  sh "sed -i s?DOCKER_HOST_NAME?${config.dockerDomainName}?g /tmp/templates/docker_swarm.crt.tpl"
  sh "sed -i s?DOCKER_HOST_IP?${DOCKER_IP_ADDRESS}?g /tmp/templates/docker_swarm.crt.tpl"
  // docker_swarm.key
  sh 'sed -i s?ROLE_NAME?vetservices?g /tmp/templates/docker_swarm.key.tpl'
  sh "sed -i s?DOCKER_HOST_NAME?${config.dockerDomainName}?g /tmp/templates/docker_swarm.key.tpl"
  sh "sed -i s?DOCKER_HOST_IP?${DOCKER_IP_ADDRESS}?g /tmp/templates/docker_swarm.key.tpl"

  // Get our Certificates
  withCredentials([string(credentialsId: "${config.vaultCredID}", variable: 'JENKINS_VAULT_TOKEN')]) {
    sh "consul-template -once -config=/tmp/templates/consul-template-config.hcl -vault-addr=${config.vaultAddress} -vault-token=${JENKINS_VAULT_TOKEN}"
  }

  //Load the CA certificate into the trusetd keystore
  echo "Java Home: $JAVA_HOME"
  echo "Importing CA certificate into $JAVA_HOME/jre/lib/security/cacerts"
  sh "keytool -importcert -alias vault-pipeline -keystore $JAVA_HOME/jre/lib/security/cacerts -noprompt -storepass changeit -file ${env.DOCKER_CERT_LOCATION}/ca.crt"

  // Load the key and certificate in a keystore
  sh "openssl pkcs12 -export -in ${env.DOCKER_CERT_LOCATION}/docker_swarm.crt -inkey ${env.DOCKER_CERT_LOCATION}/docker_swarm.key -name ${config.dockerDomainName} -out docker_swarm.p12 -password pass:changeit"
  if (!fileExists("${env.DOCKER_CERT_LOCATION}/docker_swarm.jks")) {
    sh "keytool -importkeystore -deststorepass changeit -destkeystore ${env.DOCKER_CERT_LOCATION}/docker_swarm.jks -srckeystore docker_swarm.p12 -srcstoretype PKCS12 -srcstorepass changeit"
  }
  return "${env.DOCKER_CERT_LOCATION}/docker_swarm.jks"
}