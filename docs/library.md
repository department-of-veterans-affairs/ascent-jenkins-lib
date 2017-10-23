# Java Library built with Maven
This pipeline will build a JAR artifact from a Java project using Maven as its build tool. The pipeline models the following workflow:

![Build Process Flow](/docs/images/Build.png)

# Using this pipeline
To use this pipeline include the following in your Jenkinsfile:
```groovy
mavenPipeline {
    //directory = '.'
}
```

## Configuration
* __directory__ - Path to the directory containing your pom.xml file. This defaults to the root of the repository.