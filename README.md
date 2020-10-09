# Raven-Mapper-Api

## Introduction

Raven-Mapper-Api is a backend api service for the  Raven Mortality Case Management System. Raven-Mapper-Api is tightly integrated to a fhir server and it's datasource, and it is recommended to deploy Raven-Mapper-Api with [raven-fhir-server](https://github.com/MortalityReporting/raven-fhir-server) as the providing datasource.

Raven-Mapper-Api provides:

* An importing web service for converting csv data to VRDR-fhir, and submitting to a raven-fhir-server.
* An exporting web service for retrieving and packaging case data out of the raven-fhir-server, and submitting to one of several different state registrar in standard VRDR format.

## Requirements And Installation

Requirements for installation are:
* [Java-jdk version 10 or higher](https://www.oracle.com/java/technologies/javase-downloads.html)
* [The build tool maven](http://maven.apache.org/)
* A version of the VRDR java library. The correct version of the library is contained as a git submodule in this repository.

### Installation

Install VRDR javalib: Move into ```${PROJECT_HOME}/VRDR_javalib``` and run ```mvn install``` from the command line.

Package the main project into a war: Move into ```${PROJECT_HOME}``` and run ```mvn package```.

Run locally: Move into ```${PROJECT_HOME}``` and run ```mvn spring-boot:run```. To deploy to the ```localhost:8080``` endpoint

### Dockerfile alternative

You can instead use the Dockerfile provided above to run a containerized version. This requires:

* [An installation of Docker in the local environment](https://www.docker.com/get-started)

Build the container: Move into ```${PROJECT_HOME}``` and run ```docker build -t raven-mapper-api .```

Run the container: ```docker run -p 80:80 raven-mapper-api```

## Configuration

All configuration for the project can be found in ```src/main/resources/application.properties```. All current configurable properties are set to read environment variables and are in the form ```some.key.path=${ENVIRONMENT_VARIABLE_HERE}```. It is recommended to replace these environment variables with hard-coded values to reflect your environment.

* ```fhircms.url``` A full url definition to the fhir base of the current raven-fhir-server
* ```fhircms.basicAuth.username``` If [basic authentication](https://swagger.io/docs/specification/authentication/basic-authentication/) is used to access raven-fhir-server, specify the username to authenticate with.
* ```fhircms.basicAuth.password``` If [basic authentication](https://swagger.io/docs/specification/authentication/basic-authentication/) is used to access raven-fhir-server, specify the password to authenticate with.
* ```fhircms.submit=true```. Specifies whether to use [fhir's batch submission](https://www.hl7.org/fhir/http.html#transaction) when importing into the system. Should not be changed unless you know what you're doing.
* ```canary.url=${CANARY_URL}```. Specifies a [canary instance](https://github.com/nightingaleproject/canary/) url for validating VRDR records before exporting records from the cms. If left blank, the VRDR records won't be validated from the canary tool, and is unnecessary if a canary instance is unavailable for use.
*```submission.sources.sourceurl[x]``` Specifies a url source to export towards in the "export-all" workflow. The service will export VRDR records to each url specified. Can be omitted if the export-targeted mode is used instead (more on that in the exporting section)
*```submission.sources.mode[x]``` Specifies a workflow submission mode to export towards in the "export-all" workflow. The currently supported modes are "axiell", "nightingale", and "vitalcheck", which are 3 different state registrar vendors with different workflows for exporting. As more vendors become available, more modes will be added to the system. Can be omitted if the export-targeted mode is used instead (more on that in the exporting section)

## Importing Tool
Importing is accomplished through a web client ui hosted at the web root url. Once you are deploy, access the root with your web client, and you'll be greeted with a file selector. Selecting a csv of case data will allow the user to import the case data, convert to fhir, and submit to the raven-fhir-server. A UI report of each case imported, their name, age, and import status is shown in a table for review.

For an example csv, the project has attached at the top level ```ConnectathonTestCase122221.csv```. For a detailed description of the csv structure, refer to the furthur documentation here(ED Note: To finish).

##Exporting Tool

To use the exporting tool, you must make a REST request to the ```/submitEDRS``` endpoint
### /submitEDRS parameters

* '''endpointURL''': A url definition of a receiving EDRS system
* '''endpointMode''': A workflow mode of submitting, based on receiving system. 3 systems are supported right now: "Nightingale", "Axiell", and "Vitalcheck".
* '''systemIdentifier''': A patient identifier system within the fhir system for the patient to be submitted. Works with ```codeIdentifier``` to define a full patient identifier.
* '''codeIdentifier''': A patient identifier code within the fhir system for the patient to be submitted. Works with ```systemIdentifier``` to define a full patient identifier.

If no parameters are supplied then the configuration endpoints are used instead, as discussed in the configuration section
