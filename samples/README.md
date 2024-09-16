------------
Plugin Usage
------------
```
mvn install -P{profile} -DprojectId=${project} -Dfile={path}

  # Options

  -P<profile>
    Pick a profile in the pom.xml.
    Apigee API Hub location, config directory, option are picked from the profile.

  -Dapigee.apihub.config.options
    none   - No action (default)
    create - Create when not found. Pre-existing config is NOT updated even if it is different.
    update - Update when found; create when not found
    delete - Delete when found
    sync   - Delete and recreate.
    
  -Dapigee.apihub.config.dir
  	path to the directory containing the configuration
  
  -Dbearer
  	access token. Service Account file takes precedence
    
```


# Samples

## Prerequisites (Apigee Hub setup)
- Follow the [steps](https://cloud.google.com/apigee/docs/api-hub/get-started-api-hub) to provision Apigee Hub
- Create a Service Account with the Apigee API hub permissions, download the service account key file

## API Configuration

- Check out the samples that includes the structure of the API, specs, deployment and other config objects needed to push an API to the API Hub


### Basic Implementation

**Please ensure all prerequisites have been followed prior to continuing.**

```
/samples
```

This project demonstrates use of apigee-apihub-maven-plugin to push API to a Apigee API hub. 

To use, edit samples/pom.xml and update values as specified.

```xml
	<apigee.apihub.projectId>${projectId}</apigee.apihub.projectId> <!-- GCP Project ID where Apigee API hub is provisioned -->
	<apigee.apihub.location>${location}</apigee.apihub.location> <!-- Apigee API hub location. Default is global -->
	<apigee.apihub.config.dir>./</apigee.apihub.config.dir> <!-- Directory where specs are accessible. Using ./specs for sample -->
	<apigee.apihub.config.options>${options}</apigee.apihub.config.options> <!-- Options like none, create, update, delete, sync. Default is none-->
	<apigee.apihub.serviceaccount.file>${file}</apigee.apihub.serviceaccount.file> <!-- Service Account File. Use this or "apigee.apihub.bearer". Service Account takes precedence -->
	<apigee.apihub.bearer>${bearer}</apigee.apihub.bearer> <!-- Bearer Token. Use this or  "apigee.apihub.serviceaccount.file" -->
```

To run, jump to the sample project `cd /samples` and run 

```bash
mvn install -P{profile} -DprojectId={projectId} -Doptions={option} -Dfile={path}
```
where
-  `profile` is the Maven profile in the pom.xml
-  `projectId` is the GCP Project ID where Apigee Hub is provisioned
-  `options` to either "create", "update", "delete" or "sync" the API into the API Hub
-  `path` is the path to your Service Account Key file


