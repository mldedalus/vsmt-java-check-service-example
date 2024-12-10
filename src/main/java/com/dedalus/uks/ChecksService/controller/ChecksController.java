package com.dedalus.uks.ChecksService.controller;

import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dedalus.uks.ChecksService.utils.Utilities;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ActivityDefinition;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class ChecksController extends BaseController {

    @GetMapping("/health")
    public ResponseEntity<String> getHealth() {

        // Create the outer JSON object
        Map<String, Object> outerObject = new HashMap<>();
        outerObject.put("health", "Ok!"); // Add the list of objects

        // Create an ObjectMapper instance
        ObjectMapper objectMapper = new ObjectMapper();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        try {
            // Convert the map to JSON string
            String jsonString = objectMapper.writeValueAsString(outerObject);
            return ResponseEntity.ok().headers(headers).body(jsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to convert map to JSON string", e);
        }

    }


    @PostMapping("/check")
    public ResponseEntity<String> checkTask(@RequestBody String taskResource) {
        
        RequestParseResult parseResult = validateTaskRequest(taskResource);

        MultiValueMap<String, String> responseHeaders = new LinkedMultiValueMap<String, String>();
        responseHeaders.add("Content-Type", "application/fhir+json");

        IParser jsonParser = checkService.getFHIRContext().newJsonParser();

        if (!parseResult.isValid()) {
            // Return the OperationOutcome as the response body with a 400 Bad Request status
            return new ResponseEntity<>(jsonParser.encodeResourceToString(parseResult.getOperationOutcome()), HttpStatus.BAD_REQUEST);
        }
        
        ActivityDefinition activityDefinition = parseResult.getActivityDefinition();
    
        /// Implement Check Logic Here

        OperationOutcome operationOutcome = new OperationOutcome(); 
        if (activityDefinition.getCode().getCoding().get(0).getCode().equals("code-format")) {

            if (parseResult.getFocusResource() instanceof ValueSet) {
                operationOutcome = checkService.checkCodeFormat((ValueSet)parseResult.getFocusResource(), activityDefinition.getCode().getCoding().get(0).getCode());
            } else {
                OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
                issue.setSeverity(IssueSeverity.ERROR);
                issue.setCode(IssueType.INVALID);
                issue.setDiagnostics("Focus resource needs to be a ValueSet for check code format: " + activityDefinition.getCode().getCoding().get(0).getCode() + ". it is currently a " + parseResult.getFocusResource().getClass().getSimpleName());
                operationOutcome.addIssue(issue);
                return new ResponseEntity<>(jsonParser.encodeResourceToString(operationOutcome), responseHeaders, HttpStatus.BAD_REQUEST);
            }

        } else {
            throw new RuntimeException("Unsupported ActivityDefinition: " + activityDefinition.getCode().getCoding().get(0).getCode());
        }

        // Return the OperationOutcome as the response body with a 200 OK status
        String operationOutcomeJson = jsonParser.encodeResourceToString(operationOutcome);
        return new ResponseEntity<>(operationOutcomeJson, responseHeaders, HttpStatus.OK);
    }

    @PostMapping("/validateConceptFormat")
    public ResponseEntity<String> validateConceptFormat(@RequestBody String parameters) {
        
        RequestParseResult parseResult = validateFormatCodingRequest(parameters);

        MultiValueMap<String, String> responseHeaders = new LinkedMultiValueMap<String, String>();
        responseHeaders.add("Content-Type", "application/fhir+json");

        IParser jsonParser = checkService.getFHIRContext().newJsonParser();

        if (!parseResult.isValid()) {
            // Return the OperationOutcome as the response body with a 400 Bad Request status
            return new ResponseEntity<>(jsonParser.encodeResourceToString(parseResult.getOperationOutcome()), responseHeaders, HttpStatus.BAD_REQUEST);
        }
        
        OperationOutcome operationOutcome = new OperationOutcome(); 

        // Return the OperationOutcome as the response body with a 200 OK status
        String operationOutcomeJson = jsonParser.encodeResourceToString(operationOutcome);
        return new ResponseEntity<>(operationOutcomeJson, responseHeaders, HttpStatus.OK);
    }

    private RequestParseResult validateFormatCodingRequest(String parametersResource) {

        RequestParseResult results = new RequestParseResult();

        // Parse the incoming JSON FHIR Task resource using HAPI FHIR
        IParser jsonParser = checkService.getFHIRContext().newJsonParser();
        
        Parameters parameters;
        OperationOutcome operationOutcome = new OperationOutcome();

        try {
            parameters = jsonParser.parseResource(Parameters.class, parametersResource);
        } catch (Exception e) {
            // If parsing fails, create an OperationOutcome with an error
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.INVALID);
            issue.setDiagnostics("Failed to parse Parameters resource: " + e.getMessage());
            operationOutcome.addIssue(issue);

            results.setValid(false     );
            results.setOperationOutcome(operationOutcome);
            return results;
        }

        // Extract the Coding object
        Coding coding = (Coding) parameters.getParameter().stream()
                .filter(param -> "concept".equals(param.getName()))
                .map(ParametersParameterComponent::getValue)
                .findFirst()
                .orElse(null);

        if (coding.getSystem().toLowerCase().equals(Utilities.SNOMED_SYSTEM_URI)) {
            OperationOutcome validationResult = sctValidationService.validateIdentifierFormat(coding.getCode());

            // Count issues of severity ERROR
            long errorCount = validationResult.getIssue().stream().filter(issue -> issue.getSeverity() == IssueSeverity.ERROR).count();
            if (errorCount > 0) {
                results.setValid(false);
                results.setOperationOutcome(validationResult);
                return results;
            }
        }

        results.setValid(true);
        return results;
    }

}
