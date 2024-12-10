# vsmt-checks-java-service-example

This is an example project that gives an example of how to implement a VSMT Check Endpoint using Java and the HAPI libraries.


## Deployment
The application is designed to be run as a Docker container, and runs as a Spring Boot Web API application.


## Running Locally
To run locally, simply execute as a Maven application

### Create a config file
Create a file locally with content like below, and place it on a path, such as ```/opt/application.yml```
```
# application.yml
server:
  servlet:
    context-path: /api
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain,application/javascript,text/css
    min-response-size: 1024
checks:
  terminologyServer:
      endpoint: https://services.vsmt.dc4h.link/authoring/fhir
      authenticationEndpoint: https://services.vsmt.dc4h.link/authorisation/auth/realms/terminology/protocol/openid-connect/token
      client_id: your-client-id
      client_secret: your-client-secret
  fhirServer:
      endpoint: https://services.vsmt.dc4h.link/fhir
      authenticationEndpoint: https://services.vsmt.dc4h.link/authorisation/auth/realms/terminology/protocol/openid-connect/token
      client_id: your-client-id
      client_secret: your-client-secret
```
Ensure ```SPRING_CONFIG_LOCATION``` env var is set and points to the configuration file, for example:

```SPRING_CONFIG_LOCATION=/opt/application1.yml mvn spring-boot:run```

## Build and Run Container
### Build the Image
```docker build --no-cache . -t vsmt-check-java-service```

### Deployment Configuration
It is expected that the configuration file will be mounted via a Kubernetes ConfigMap volume mount, and will be available at /opt/application.yml

### Running Docker Image Locally

This mounts your local file ```/opt/application.yml``` into the container at ```/tmp/application.yml```, which is where the application expects to find it, as per the ```CONFIG_FILE_PATH``` env var.
```
docker run -p 8080:8080 \
 -e SPRING_CONFIG_LOCATION=/tmp/application.yml \
 -v /opt/application.yml:/tmp/application.yml \
 vsmt-check-java-service
 ```

 ### Testing
 ```
 curl --location 'http://127.0.0.1:8080/api/check' \
--header 'Content-Type: application/json' \
--data '{
  "resourceType": "Task",
  "instantiatesCanonical": "http://dedalus.com/fhir/ActivityDefinition/code-format",
  "status": "requested",
  "intent": "order",
  "priority": "stat",
  "code": {
    "coding": [
      {
        "system": "http://hl7.org/fhir/CodeSystem/task-code",
        "code": "fulfill"
      }
    ]
  },
  "focus": {
    "reference": "#example-value-set"
  },
  "authoredOn": "2024-10-09T08:25:05+10:00",
  "lastModified": "2024-10-09T08:25:05+10:00",
  "contained": [
    {
      "resourceType": "ValueSet",
      "id": "example-value-set",
      "url": "http://example.org/fhir/ValueSet/example",
      "status": "active",
      "compose" : {
        "include" : [
        {
            "system" : "http://snomed.info/sct",
            "filter" : [
            {
                "property" : "expression",
                "op" : "=",
                "value" : "< 71388002 |Procedure| : 363704007 |Procedure site| = << 64033007 |Kidney structure|"
            }],
            "concept":
            [
                {
                    "code": "3424234324234234324",
                    "display": "Madeupcode"
                }
            ]
        }]
       },
       "expansion" : {
            "contains" : [{
                "system" : "http://snomed.info/sct",
                "code" : "fake-snomed-code",
                "display" : "Alkaline phosphatase - bile isoenzyme level"
            },
            {
                "system" : "http://snomed.info/sct",
                "code" : "991013181000000104",
                "display" : "Alkaline phosphatase - bile isoenzyme level"
            }]
        }
    }
  ]
}
'
```