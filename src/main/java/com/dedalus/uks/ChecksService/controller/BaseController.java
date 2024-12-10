package com.dedalus.uks.ChecksService.controller;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ActivityDefinition;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import com.dedalus.uks.ChecksService.exception.ActivityDefinitionManyFoundException;
import com.dedalus.uks.ChecksService.exception.ActivityDefinitionNotFoundException;
import com.dedalus.uks.ChecksService.service.ChecksService;
import com.dedalus.uks.ChecksService.service.SCTValidationService;

import lombok.extern.slf4j.Slf4j;
import ca.uhn.fhir.parser.IParser;

@Slf4j
public abstract class BaseController {

    @Autowired
    public ChecksService checkService;

    @Autowired
    public SCTValidationService sctValidationService;

    public RequestParseResult validateTaskRequest(String taskResource) {

        RequestParseResult results = new RequestParseResult();

        // Parse the incoming JSON FHIR Task resource using HAPI FHIR
        IParser jsonParser = checkService.getFHIRContext().newJsonParser();
        
        Task task;
        OperationOutcome operationOutcome = new OperationOutcome();

        try {
            task = jsonParser.parseResource(Task.class, taskResource);
        } catch (Exception e) {
            // If parsing fails, create an OperationOutcome with an error
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.INVALID);
            issue.setDiagnostics("Failed to parse Task resource: " + e.getMessage());
            operationOutcome.addIssue(issue);

            results.setValid(false     );
            results.setOperationOutcome(operationOutcome);
            return results;
        }

        // Check if Task.focus is referencing a ValueSet
        if (task.hasFocus() && task.getFocus().getReference() != null) {
            log.info("Task focus reference is: " + task.getFocus().getReference() + " Trying to resolve.");

            // Try to resolve the referenced resource (could be contained within the Task or external)
            IBaseResource focusResource = checkService.resolveFocusResource(task, checkService.getTerminologyServer());
            
            // Check if the resource is a ValueSet
            if (!(focusResource instanceof ValueSet)) {
                OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
                issue.setSeverity(IssueSeverity.ERROR);
                issue.setCode(IssueType.INVALID);
                issue.setDiagnostics("Task focus is not a ValueSet, but it should be.");
                operationOutcome.addIssue(issue);

                results.setValid(false     );
                results.setOperationOutcome(operationOutcome);
                return results;
            } else {
                log.info("Task focus resolved successfully to ValueSet: " + ((ValueSet)focusResource).getUrl());
            }

        } else {
            // If Task.focus is missing, return an error
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.REQUIRED);
            issue.setDiagnostics("Task.focus is missing or null.");
            operationOutcome.addIssue(issue);
            results.setValid(false     );
            results.setOperationOutcome(operationOutcome);
            return results;
        }

        ActivityDefinition activityDefinition = null;
        //Ensure the instantiteCanonical is an ActivityDefinition
        try {
             activityDefinition = checkService.resolveActivityDefinition(task.getInstantiatesCanonical());
             results.setActivityDefinition(activityDefinition);
        } catch (ActivityDefinitionNotFoundException nfe) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.INVALID);
            issue.setDiagnostics("ActivityDefinition not found for canonical URL: " + task.getInstantiatesCanonical());
            operationOutcome.addIssue(issue);
            results.setValid(false     );
            results.setOperationOutcome(operationOutcome);
            return results;

        } catch (ActivityDefinitionManyFoundException mfe) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.INVALID);
            issue.setDiagnostics("Multiple ActivityDefinitions found for canonical URL: " + task.getInstantiatesCanonical());
            operationOutcome.addIssue(issue);
            results.setValid(false     );
            results.setOperationOutcome(operationOutcome);
            return results;
        }
       



        // Check the focus is a valueset
        IBaseResource focusResource = null;
        OperationOutcome invalidValueset = isFocusValueSet(task);
        if (invalidValueset != null) {
            results.setValid(false     );
            results.setOperationOutcome(operationOutcome);
            return results;
        } else {
            focusResource = checkService.resolveFocusResource(task, checkService.getTerminologyServer());
            if (focusResource == null) {
                OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
                issue.setSeverity(IssueSeverity.ERROR);
                issue.setCode(IssueType.INVALID);
                issue.setDiagnostics("Focus resource not resolvable in Focus: " + task.getFocus());
                operationOutcome.addIssue(issue);
                results.setValid(false     );
                results.setOperationOutcome(operationOutcome);
                return results;
            } else {
                results.setFocusResource(focusResource);
            }
        }

        results.setValid(true     );
        return results;
    }

    


    private OperationOutcome isFocusValueSet(Task task) {
        // Check if Task.focus is referencing a ValueSet
        if (task.hasFocus() && task.getFocus().getReference() != null) {
            // Try to resolve the referenced resource (could be contained within the Task or external)
            IBaseResource focusResource = checkService.resolveFocusResource(task, checkService.getTerminologyServer());
            
            // Check if the resource is a ValueSet
            if (!(focusResource instanceof ValueSet)) {
                OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
                issue.setSeverity(IssueSeverity.ERROR);
                issue.setCode(IssueType.INVALID);
                issue.setDiagnostics("Task focus is not a ValueSet, but it should be.");
                OperationOutcome operationOutcome = new OperationOutcome();
                operationOutcome.addIssue(issue);
                return operationOutcome;
            }
        } else {
            // If Task.focus is missing, return an error
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.REQUIRED);
            issue.setDiagnostics("Task.focus is missing or null.");
            OperationOutcome operationOutcome = new OperationOutcome();
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        return null;
    }


    public class RequestParseResult {

        private boolean isValid;
        public boolean isValid() {
            return isValid;
        }
        public void setValid(boolean isValid) {
            this.isValid = isValid;
        }

        private OperationOutcome operationOutcome;
        public OperationOutcome getOperationOutcome() {
            return operationOutcome;
        }
        public void setOperationOutcome(OperationOutcome operationOutcome) {
            this.operationOutcome = operationOutcome;
        }


        private ActivityDefinition activityDefinition;
        public ActivityDefinition getActivityDefinition() {
            return activityDefinition;
        }
        public void setActivityDefinition(ActivityDefinition activityDefinition) {
            this.activityDefinition = activityDefinition;
        }

        private IBaseResource focusResource;
        public IBaseResource getFocusResource() {
            return focusResource;
        }
        public void setFocusResource(IBaseResource focusResource) {
            this.focusResource = focusResource;
        }

    }
    
}
