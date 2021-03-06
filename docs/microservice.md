# Microservice Pipeline
This pipeline will build both an executable JAR file using Maven and then a Docker image containing the previously built JAR file. The newly built Docker image
will be deployed to the CI docker swarm and automated test cases will be executed against the deployed service. When test case execution is complete, the test
environment is torn down.

![Build Process Flow](/docs/images/microservice.png)

# Using this pipeline
To use this pipeline include the following in your Jenkinsfile:
```groovy
microservicePipeline {
    dockerBuilds = [
        "ascent/demo-service": "ascent-demo-service",
        "ascent/document-service": "ascent-document-service"
    ]

    //Testing Info
    testEnvironment = [
        'docker-compose.yml'
    ]
    serviceToTest = 'demo-service' //This must match the service name defined in the testEnvironment compose file
    //directory = '.'
    //mavenSettings = '/path/to/maven/settings.xml'
}
```

## Configuration
* __mavenSettings__ - Specify a custom maven settings.xml file. By default it uses [settings.xml](/resources/gov/va/maven/settings.xml)
* __dockerBuilds__ - (Required) A map of image tags to directories. Each directory is expected to contain a Dockerfile.
* __directory__ - Path to the directory containing your pom.xml file. This defaults to the root of the repository.
* __testEnvironment__ - An array of docker compose files that define the test environment to deploy for automated testing