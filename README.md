# apigee-apihub-maven-plugin

----------------
About the Plugin
----------------

apigee-apihub-maven-plugin is a utility for creating, updating, deleting APIs into the Apigee API Hub. For more info on Apigee API Hub, check [this link](https://cloud.google.com/apigee/docs/api-hub/what-is-api-hub)

The code is distributed under the Apache License 2.0.


The [samples folder](./samples) provides a README with Getting Started steps and commands to hit the ground quickly. 

## Prerequisites
You will need the following to run the samples:
- Apigee Edge developer account (in an Apigee hybrid org)
- [Java SDK >= 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
- [Maven 3.x](https://maven.apache.org/)

## Plugin Usage

To use the plugin, add the following dependency to your pom

```xml
<dependency>
  <groupId>com.apigee.apihub.config</groupId>
  <artifactId>apigee-apihub-maven-plugin</artifactId>
  <version>1.x.x</version>
</dependency>
```

### Command

```
mvn install -P<profile> -Dapigee.apihub.config.dir=$path -Dapigee.apihub.config.options=$option
```

#### Options

```
-P<profile>
    Pick a profile in the parent pom.xml (shared-pom.xml in the example).
    Apigee org and env information comes from the profile.
    
-Dapigee.apihub.config.options
    none   - No action (default)
    create - Create when not found. Pre-existing config is NOT updated even if it is different.
    update - Update when found; create when not found
    delete - Delete when found
    sync   - Delete and recreate.

-Dapigee.apihub.config.dir
    path to the directory containing the configuration
```

#### Individual goals

To execute individual goals, you can use the prefix `apigee-apihub:<goal>`, for example `apigee-apihub:attributes`

The list of goals available are:
- apis
- apiversions
- specs
- attributes
- dependencies
- externalapis
- deployments

An example to just configure attributes will look like

```
mvn apigee-apihub:attributes -Pdev -Dapigee.apihub.config.options=create -Dapigee.apihub.config.dir=./config
```

**NOTE:** The config files must be in a single directory and should match the below naming conventions:

| Goal       	| File name 			|
| --------   	| ------- 			|
| apis		 	| apis.json			|
| apiversions	| apiVersions.json	|
| specs			| specs.json			|
| attributes 	| attributes.json	|
| externalapis  | externalApis.json	|
| dependencies  | dependencies.json	|
| deployments	| deployments.json	|


## Support
* Please send feature requests using [issues](https://github.com/apigee/apigee-apihub-maven-plugin/issues)
* Post a question in [Apigee community](https://community.apigee.com/index.html)
* Create an [issue](https://github.com/apigee/apigee-apihub-maven-plugin/issues/new)

## Disclaimer
This is not an officially supported Google product.
