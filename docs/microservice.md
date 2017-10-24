# Microservice Pipeline
This pipeline will build both an executable JAR file using Maven and then a Docker image containing the previously built JAR file.

![Build Process Flow](/docs/images/microservice.png)

# Using this pipeline
To use this pipeline include the following in your Jenkinsfile:
```groovy
dockerPipeline {
    dockerBuilds = [
        "ascent/demo-service": "ascent-demo-service",
        "ascent/document-service": "ascent-document-service"
    ]
    //directory = '.'
    //imageName = 'ascent/ascent-base'
}
```

## Configuration
* __dockerBuilds__ - (Required) A map of directories to image tags. Each directory is expected to contain a Dockerfile. Use this configuration option if your repository container multiple Docker images.
* __imageName__ - (Required if dockerBuilds parameter is not set) The name to tag the image with. Use this parameter if your repository contains a single Docker image.
* __directory__ - Path to the directory containing your pom.xml file. This defaults to the root of the repository.
