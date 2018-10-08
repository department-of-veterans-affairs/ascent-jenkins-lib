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
    config.keystoreAlias = config.dockerDomainName
  }

  if(config.caFileName == null) {
    config.caFilePrefix = config.keystoreAlias
  }

  if(config.certFileName == null) {
    config.certFileName = config.keystoreAlias
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
  // edit the placement of the certificates
  sh "sed -i s?CERT_FILE_NAME?${config.certFileName}?g /tmp/templates/consul-template-config.hcl"
  sh "sed -i s?CA_FILE_NAME?${config.caFileName}?g /tmp/templates/consul-template-config.hcl"

  // Get our Certificates
  withCredentials([string(credentialsId: "${config.vaultCredID}", variable: 'JENKINS_VAULT_TOKEN')]) {
    sh "consul-template -once -config=/tmp/templates/consul-template-config.hcl -vault-addr=${config.vaultAddress} -vault-token=${JENKINS_VAULT_TOKEN}"
  }

  //Load the CA certificate into the trusetd keystore
  echo "Importing CA certificate into /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/cacerts"
  try {
    sh "keytool -importcert -alias ${config.keystoreAlias} -keystore /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/cacerts -noprompt -storepass changeit -file ${env.DOCKER_CERT_LOCATION}/${config.caFileName}.crt"
  } catch(Exception ex) {
    sh "keytool -delete -alias ${config.keystoreAlias} -keystore /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/cacerts -storepass changeit"
    sh "keytool -importcert -alias ${config.keystoreAlias} -keystore /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/cacerts -noprompt -storepass changeit -file ${env.DOCKER_CERT_LOCATION}/${config.caFileName}.crt"
  }
  sh "keytool -list -alias ${config.keystoreAlias} -keystore /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/security/cacerts -storepass changeit"

  // Load the key and certificate in a keystore
  sh "openssl pkcs12 -export -in ${env.DOCKER_CERT_LOCATION}/${config.certFileName}.crt -inkey ${env.DOCKER_CERT_LOCATION}/${config.certFileName}.key -name ${config.dockerDomainName} -out ${config.certFileName}.p12 -password pass:changeit"
  if (!fileExists("${env.DOCKER_CERT_LOCATION}/${config.certFileName}.jks")) {
    sh "keytool -importkeystore -deststorepass changeit -destkeystore ${env.DOCKER_CERT_LOCATION}/${config.certFileName}.jks -srckeystore ${config.certFileName}.p12 -srcstoretype PKCS12 -srcstorepass changeit"
  }
  sh "keytool -list -keystore ${env.DOCKER_CERT_LOCATION}/${config.certFileName}.jks -storepass changeit"
  sh "mvn -version"
  return "${env.DOCKER_CERT_LOCATION}/${config.certFileName}.jks"
}
