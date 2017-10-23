# Docker Image
This pipeline will and publish a Docker image to your Docker Repository.

![Build Process Flow](/docs/images/docker_build.png)

# Using this pipeline
To use this pipeline include the following in your Jenkinsfile:
```groovy
dockerPipeline {
    //directory = '.'
    //imageName = 'ascent/ascent-base'
}
```

## Configuration
* __imageName__ - (Required) The name to tag the image with.
* __directory__ - Path to the directory containing your pom.xml file. This defaults to the root of the repository.
