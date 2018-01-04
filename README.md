# ascent-jenkins-lib
Shared Library for Jenkins Pipelines

# Sonar Configuration
On your Sonar server you will need to install the following plugins:
* [GitHub Plugin](https://docs.sonarqube.org/display/PLUG/GitHub+Plugin)

# Jenkins Configuration
On your Master Jenkins server you will need to install the following plugins:
* docker-workflow
* github-branch-source
* envinject
* sonar
* pipeline-utilities-steps

## Credentials
The following credentials will need to be setup in Jenkins:

| Credential ID  | Credential Type | Notes
| ------------- | ------------- | -------------- |
| nexus  | username/password | |
| docker-repository | username/password  | |
| aws | username/password | The username should be your ACCESS_ID and the password is the SECRET_KEY value |

## Global properties
Open the `/configure` url for your Jenkins Master server and add the following Global Properties:
* Name: `DOCKER_HOST` Value: `URL for your Docker Host (ex. tcp://docker.test.com:2375)`
* Name: `DOCKER_REPOSITORY_URL` Value: `URL for your Docker Repository (ex. https://index.docker.io/v1/)`
* Name: `CI_DOCKER_SWARM_MANAGER` Value: `URL for a Docker Swarm manager in the CI environment (ex. tcp://manager.test.com:2375)`
* Name: `VAULT_ADDR` Value: `URL for the Vault server`

## SonarQube Servers
As are prequiste for configuring Sonar integration, you will need to create an access token that Jenkins can use to authenticate with Sonar.
To do this following these steps:
1. Open your browser to <Your Sonar URL>/account/security/.
1. Enter a name for your token in the __Generate New Token__ field and click __Generate__.
1. Copy the token value and save that for later.

Setup a Webhook to call back Jenkins when the scan is complete. See [SonarQube Plugin](https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Jenkins#AnalyzingwithSonarQubeScannerforJenkins-AnalyzinginaJenkinspipeline) for more information.
1. Add a webhook named Jenkins and a URL of {you jenkins instance url}/sonarqube-webhook/. Make sure to retain the trailing slash.

Open the `/configure` url for your Jenkins Master server and in the SonarQube section:
1. Click the __Add SonarQube__ button
1. Set the name to `CI`
1. Set the server URL to `Your Sonar server URL`
1. Set the server authentication token to the token value you created earlier.

## Global Pipeline Libraries
In order to use this library you will need to configure it as a Global Library within Jenkins.
1. Click the __Add__ button.
1. Give the library a unique name. For example: 'ascent'
1. Set the __Default version__ to 'master'
1. Check the __Allow default version to be overridden__ option.
1. Select  __Modern SCM__ as the retrieval method.
1. Github should be selected as the SCM implementation
1. Select or add the credentials used to authenticate to Github
1. Enter 'department-of-veterans-affairs' as the __Owner__
1. Enter 'ascent-jenkins-lib' as the __Repository__

# Referencing from your Jenkinsfile
To use this shared library you will need to reference it from the Jenkinsfile in your source code repository. This can be done by adding the following line to the beginning of your Jenkinsfile. In this example the name given to the shared library is `ascent`, change this value to match the name you gave the shared library.
```groovy
@Library('ascent') _
```

## Project Types
This library supports pipelines for the following project types:
* [Java Library](docs/library.md)
* [Docker Image](docs/docker.md)
* [Microservice](docs/microservice.md)
