package com.dedalus.uks.ChecksService.service;
import com.dedalus.uks.ChecksService.config.ChecksConfig;
import com.dedalus.uks.ChecksService.config.ChecksConfig.IFhirRestServer;
import com.dedalus.uks.ChecksService.exception.ActivityDefinitionManyFoundException;
import com.dedalus.uks.ChecksService.exception.ActivityDefinitionNotFoundException;
import com.dedalus.uks.ChecksService.utils.Utilities;
import com.fasterxml.jackson.core.JsonProcessingException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.SortOrderEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ActivityDefinition;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;

import java.util.ArrayList;
import java.util.List;

import org.snomed.langauges.ecl.ECLObjectFactory;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.langauges.ecl.domain.ConceptReference;
import org.snomed.langauges.ecl.domain.Pair;
import org.snomed.langauges.ecl.domain.expressionconstraint.CompoundExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.DottedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.ExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.RefinedExpressionConstraint;
import org.snomed.langauges.ecl.domain.expressionconstraint.SubExpressionConstraint;
import org.snomed.langauges.ecl.domain.filter.*;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeGroup;
import org.snomed.langauges.ecl.domain.refinement.EclAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.EclRefinement;
import org.snomed.langauges.ecl.domain.refinement.SubAttributeSet;
import org.snomed.langauges.ecl.domain.refinement.SubRefinement;

@Service
@Slf4j
public class ChecksService {

    private final ChecksConfig checksConfig;
    private final FhirTokenService fhirTokenService;
    private final TerminologyTokenService terminologyTokenService;
    private final SCTValidationService sctValidationService;
    private final ECLQueryBuilder eclQueryBuilder;

    private FhirContext fhirContext = FhirContext.forR4();

    @Autowired
    public ChecksService(ChecksConfig checksConfig, FhirTokenService fhirTokenService, TerminologyTokenService terminologyTokenService, SCTValidationService sctValidationService) {
        this.checksConfig = checksConfig;
        this.fhirTokenService = fhirTokenService;
        this.terminologyTokenService = terminologyTokenService;
        this.sctValidationService = sctValidationService;
        this.eclQueryBuilder = new ECLQueryBuilder(new ECLObjectFactory());
    }

    public FhirContext getFHIRContext() {
        return fhirContext;
    }

    public ChecksConfig.TerminologyServer getTerminologyServer() {
        return checksConfig.getTerminologyServer();
    }
    public ChecksConfig.FhirServer getFhirServer() {
        return checksConfig.getFhirServer();
    }

    public OperationOutcome checkCodeFormat(ValueSet valueSet, String checkCode) {
        log.info("Carrying out check for concept format");
        OperationOutcome operationOutcome = new OperationOutcome();

        if (valueSet.getExpansion() == null ) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.INVALID);
            issue.setDiagnostics("ValueSet does not have an expansion");
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        // Iterate the ValueSet Compose Include to check each code
        for (ValueSet.ConceptSetComponent conceptSet : valueSet.getCompose().getInclude()) {

            if (conceptSet.getConcept() != null) {
                for (ValueSet.ConceptReferenceComponent conceptRef : conceptSet.getConcept()) {

                    if (conceptSet.getSystem().equals(Utilities.SNOMED_SYSTEM_URI)) {
                        OperationOutcome validationResult = sctValidationService.validateIdentifierFormat(conceptRef.getCode());
                        if (validationResult.getIssue().size() > 0) {
                            String expression = "ValueSet.compose.include.where(system = '" + conceptSet.getSystem() + "'" + ((conceptSet.hasVersion()) ? " and version = '" + conceptSet.getVersion() + "'" : "") + " ).concept.where(code = '" + conceptRef.getCode() + "')";
                            StringType expession = new StringType(expression);
                            List<StringType> expressionList = new ArrayList<>();
                            expressionList.add(expession);
                            for (OperationOutcomeIssueComponent issue : validationResult.getIssue()) {
                                issue.setExpression(expressionList);
                                operationOutcome.addIssue(issue);
                            }
                        }
                    }

                }
            }

            if (conceptSet.getFilter() != null) {

                String[] arrayConceptProperties = { "parent", "child", "descendant", "ancestor", "code" };

                for (ValueSet.ConceptSetFilterComponent conceptFilter : conceptSet.getFilter()) {
                    
                    // Check if the property is one of the supported properties where the value may indicate a code
                    if (Utilities.containsIgnoreCase(arrayConceptProperties, conceptFilter.getProperty())) {
                        
                        if (conceptSet.getSystem().equals(Utilities.SNOMED_SYSTEM_URI)) {
                            OperationOutcome validationResult = sctValidationService.validateIdentifierFormat(conceptFilter.getValue());
                            if (validationResult.getIssue().size() > 0) {
                                String expression = "ValueSet.compose.include.where(system = '" + conceptSet.getSystem() + "'" + ((conceptSet.hasVersion()) ? " and version = '" + conceptSet.getVersion() + "'" : "") + " ).filter.where(property = '" + conceptFilter.getProperty() + "' and value = '" + conceptFilter.getValue() + "')";
                                StringType expession = new StringType(expression);
                                List<StringType> expressionList = new ArrayList<>();
                                expressionList.add(expession);
                                for (OperationOutcomeIssueComponent issue : validationResult.getIssue()) {
                                    issue.setExpression(expressionList);
                                    operationOutcome.addIssue(issue);
                                }
                            }
                        }
 
                    }

                    // Check if the property is 'expression' where the value will indicate an ECL expression
                    if (conceptFilter.getProperty().equalsIgnoreCase("expression")) {
                        String ecl = conceptFilter.getValue();
                        ExpressionConstraint query = eclQueryBuilder.createQuery(ecl);
                        List<ExpressionConcept> concepts = eclParse(query);

                        for(ExpressionConcept expressionConcept : concepts) {
                            
                            if (conceptSet.getSystem().equals(Utilities.SNOMED_SYSTEM_URI)) {
                                OperationOutcome validationResult = sctValidationService.validateIdentifierFormat(expressionConcept.getConceptId());
                                if (validationResult.getIssue().size() > 0) {
                                    String expression = "ValueSet.compose.include.where(system = '" + conceptSet.getSystem() + "'" + ((conceptSet.hasVersion()) ? " and version = '" + conceptSet.getVersion() + "'" : "") + " ).filter.where(property = 'expression')";
                                    StringType expession = new StringType(expression);
                                    List<StringType> expressionList = new ArrayList<>();
                                    expressionList.add(expession);
                                    for (OperationOutcomeIssueComponent issue : validationResult.getIssue()) {
                                        issue.setExpression(expressionList);
                                        operationOutcome.addIssue(issue);
                                    }
                                }
                            }

                        }

                    }
                }
            } 
        }

        // Iterate the ValueSet expansion contains to check each code
        for (ValueSet.ValueSetExpansionContainsComponent concept : valueSet.getExpansion().getContains()) {
            
            if (concept.getSystem()!=null && concept.getCode()!=null) {

                if (concept.getSystem().equals(Utilities.SNOMED_SYSTEM_URI)) {
                    OperationOutcome validationResult = sctValidationService.validateIdentifierFormat(concept.getCode());
                    if (validationResult.getIssue().size() > 0) {
                        String expression = "ValueSet.expansion.contains.where(system = '" + concept.getSystem() + "' and code = '" + concept.getCode() + "'" + ((concept.hasVersion()) ? " and version = '" + concept.getVersion() + "'" : "") + ")";
                        StringType expession = new StringType(expression);
                        List<StringType> expressionList = new ArrayList<>();
                        expressionList.add(expession);
                        for (OperationOutcomeIssueComponent issue : validationResult.getIssue()) {
                            issue.setExpression(expressionList);
                            operationOutcome.addIssue(issue);
                        }
                    }
                }

            } else if (concept.getContains() != null && concept.getContains().size() > 0) {
                for (ValueSet.ValueSetExpansionContainsComponent subConcept : concept.getContains()) {

                    if (subConcept.getSystem().equals(Utilities.SNOMED_SYSTEM_URI)) {
                        OperationOutcome validationResult = sctValidationService.validateIdentifierFormat(subConcept.getCode());
                        if (validationResult.getIssue().size() > 0) {
                            String expression = "ValueSet.expansion.contains.contains.where(system = '" + subConcept.getSystem() + "' and code = '" + subConcept.getCode() + "'" + ((subConcept.hasVersion()) ? " and version = '" + subConcept.getVersion() + "'" : "") + ")";
                            StringType expession = new StringType(expression);
                            List<StringType> expressionList = new ArrayList<>();
                            expressionList.add(expession);
                            for (OperationOutcomeIssueComponent issue : validationResult.getIssue()) {
                                issue.setExpression(expressionList);
                                operationOutcome.addIssue(issue);
                            }
                        }
                    }

                }
            }

        }

        return operationOutcome;
    }

    public OperationOutcome checkCodesinRelease(ValueSet valueSet, String checkCode) {
        log.info("Carrying out check for Codes In Release");
        OperationOutcome operationOutcome = new OperationOutcome();

        if (valueSet.getExpansion() == null ) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.INVALID);
            issue.setDiagnostics("ValueSet does not have an expansion");
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }
        
        String token = getToken();

        IGenericClient client = fhirContext.newRestfulGenericClient(getTerminologyServer().getEndpoint());

        // Iterate the ValueSet Compose Include to check each code
        for (ValueSet.ConceptSetComponent conceptSet : valueSet.getCompose().getInclude()) {

            if (conceptSet.getConcept() != null) {
                for (ValueSet.ConceptReferenceComponent conceptRef : conceptSet.getConcept()) {

                    ConceptValidationResult result = validateConcept(client, conceptSet.getSystem(), conceptRef.getCode(), (conceptSet.hasVersion()) ? conceptSet.getVersion() : null, token);

                    if (result.getValid()==false) {
                        String expression = "ValueSet.compose.include.where(system = '" + conceptSet.getSystem() + "'" + ((conceptSet.hasVersion()) ? " and version = '" + conceptSet.getVersion() + "'" : "") + " ).concept.where(code = '" + conceptRef.getCode() + "')";
                        StringType expession = new StringType(expression);
                        List<StringType> expressionList = new ArrayList<>();
                        expressionList.add(expession);
                        result.getIssue().setExpression(expressionList);
                        operationOutcome.addIssue(result.getIssue());
                    }
                }
            }

            if (conceptSet.getFilter() != null) {

                String[] arrayConceptProperties = { "parent", "child", "descendant", "ancestor", "code" };

                for (ValueSet.ConceptSetFilterComponent conceptFilter : conceptSet.getFilter()) {
                    
                    // Check if the property is one of the supported properties where the value may indicate a code
                    if (Utilities.containsIgnoreCase(arrayConceptProperties, conceptFilter.getProperty())) {
                        ConceptValidationResult result = validateConcept(client, conceptSet.getSystem(), conceptFilter.getValue(), (conceptSet.hasVersion()) ? conceptSet.getVersion() : null, token);

                        if (result.getValid()==false) {
                            String expression = "ValueSet.compose.include.where(system = '" + conceptSet.getSystem() + "'" + ((conceptSet.hasVersion()) ? " and version = '" + conceptSet.getVersion() + "'" : "") + " ).filter.where(property = '" + conceptFilter.getProperty() + "' and value = '" + conceptFilter.getValue() + "')";
                            StringType expession = new StringType(expression);
                            List<StringType> expressionList = new ArrayList<>();
                            expressionList.add(expession);
                            result.getIssue().setExpression(expressionList);
                            operationOutcome.addIssue(result.getIssue());
                        }
                    }

                    // Check if the property is 'expression' where the value will indicate an ECL expression
                    if (conceptFilter.getProperty().equalsIgnoreCase("expression")) {
                        String ecl = conceptFilter.getValue();
                        ExpressionConstraint query = eclQueryBuilder.createQuery(ecl);
                        List<ExpressionConcept> concepts = eclParse(query);

                        for(ExpressionConcept expressionConcept : concepts) {
                            ConceptValidationResult result = validateConcept(client, conceptSet.getSystem(), expressionConcept.getConceptId(), (conceptSet.hasVersion()) ? conceptSet.getVersion() : null, token);

                            if (result.getValid()==false) {
                                String expression = "ValueSet.compose.include.where(system = '" + conceptSet.getSystem() + "'" + ((conceptSet.hasVersion()) ? " and version = '" + conceptSet.getVersion() + "'" : "") + " ).filter.where(property = 'expression')";
                                StringType expession = new StringType(expression);
                                List<StringType> expressionList = new ArrayList<>();
                                expressionList.add(expession);
                                result.getIssue().setExpression(expressionList);
                                operationOutcome.addIssue(result.getIssue());
                            }
                        }

                    }
                }
            } 
        }

        // Iterate the ValueSet expansion contains to check each code
        for (ValueSet.ValueSetExpansionContainsComponent concept : valueSet.getExpansion().getContains()) {
            
            if (concept.getSystem()!=null && concept.getCode()!=null) {

                ConceptValidationResult result = validateConcept(client, concept.getSystem(), concept.getCode(), (concept.hasVersion()) ? concept.getVersion() : null, token);

                if (result.getValid()==false) {
                    String expression = "ValueSet.expansion.contains.where(system = '" + concept.getSystem() + "' and code = '" + concept.getCode() + "'" + ((concept.hasVersion()) ? " and version = '" + concept.getVersion() + "'" : "") + ")";
                    StringType expession = new StringType(expression);
                    List<StringType> expressionList = new ArrayList<>();
                    expressionList.add(expession);
                    result.getIssue().setExpression(expressionList);
                    operationOutcome.addIssue(result.getIssue());
                }

            } else if (concept.getContains() != null && concept.getContains().size() > 0) {
                for (ValueSet.ValueSetExpansionContainsComponent subConcept : concept.getContains()) {
                    
                    ConceptValidationResult result = validateConcept(client, subConcept.getSystem(), subConcept.getCode(), (subConcept.hasVersion()) ? subConcept.getVersion() : null, token);

                    if (result.getValid()==false) {
                        String expression = "ValueSet.expansion.contains.contains.where(system = '" + subConcept.getSystem() + "' and code = '" + subConcept.getCode() + "'" + ((subConcept.hasVersion()) ? " and version = '" + subConcept.getVersion() + "'" : "") + ")";
                        StringType expession = new StringType(expression);
                        List<StringType> expressionList = new ArrayList<>();
                        expressionList.add(expession);
                        result.getIssue().setExpression(expressionList);
                        operationOutcome.addIssue(result.getIssue());
                    }
                    
                }
            }

        }

        return operationOutcome;
    }


    private String getToken() {
        try {
            return terminologyTokenService.getToken(getTerminologyServer().getAuthenticationEndpoint(), getTerminologyServer().getClientId(), getTerminologyServer().getClientSecret(), true);
        } catch (JsonProcessingException e) {
            log.error("Error fetching token " + e.getMessage());
            throw new RuntimeException("Error fetching token " + e.getMessage(), e);
        }
    }

    private ConceptValidationResult validateConcept(IGenericClient client, String system, String code, String version, String token) {
        
        ConceptValidationResult result = new ConceptValidationResult(true, null);
        
        Parameters params = new Parameters();
        params.addParameter().setName("url").setValue(new UriType(system));
        params.addParameter().setName("code").setValue(new CodeType(code));
        if (version!=null) {
            params.addParameter().setName("version").setValue(new StringType(version));
        }

        // Execute the $validate-code operation
        Parameters response = client
                .operation()
                .onType("CodeSystem")
                .named("$validate-code")
                .withParameters(params)
                .withAdditionalHeader("Authorization", "Bearer " + token)
                .execute();

        // Check the result
        boolean isValid = response.getParameterBool("result");
        result.setValid(isValid);

        if (isValid) {
            log.info("Code is valid in the CodeSystem: " + system);
        } else {
            String message = response.getParameter("message").getValue().toString();
            log.info("Code is NOT valid: " + message);
            OperationOutcomeIssueComponent invalidCodeIssue = new OperationOutcomeIssueComponent();
            invalidCodeIssue.setSeverity(IssueSeverity.WARNING);
            invalidCodeIssue.setCode(IssueType.CODEINVALID);
            invalidCodeIssue.setDiagnostics(message);
            result.setIssue(invalidCodeIssue);
            
        }
        return result;
    }

    public class ConceptValidationResult {
        private boolean isValid;
        private OperationOutcomeIssueComponent issue;

        public ConceptValidationResult(boolean isValid, OperationOutcomeIssueComponent issue) {
            this.isValid = isValid;
            this.issue = issue;
        }

        public boolean getValid() {
            return isValid;
        }
        public void setValid(boolean isValid) {
            this.isValid = isValid;
        }

        public OperationOutcomeIssueComponent getIssue() {
            return issue;
        }
        public void setIssue(OperationOutcomeIssueComponent issue) {
            this.issue = issue;
        }
    }

    public class ExpressionConcept {
		private String conceptId;

		public ExpressionConcept(String conceptId) {
			this.conceptId = conceptId;
		}

		public String getConceptId() {
			return conceptId;
		}
	}

    // Helper method to resolve the ActivityDefinition from a FHIR server
    public ActivityDefinition resolveActivityDefinition(String canonicalUrl) {
        // Define the base URL of the FHIR server where you expect to retrieve the resource
        String fhirServerBaseUrl = getFhirServer().getEndpoint();

        // Create a FHIR client to communicate with the FHIR server
        IGenericClient client = fhirContext.newRestfulGenericClient(fhirServerBaseUrl);

        log.info("Calling TokenService with " + getFhirServer().getAuthenticationEndpoint() + " " + getFhirServer().getClientId());
        String token = null;
        try {
            token = fhirTokenService.getToken(getFhirServer().getAuthenticationEndpoint(), getFhirServer().getClientId(), getFhirServer().getClientSecret(), false);
        } catch (JsonProcessingException e) {
            log.error("Error fetching token " + e.getMessage());
            throw new RuntimeException("Error fetching token " + e.getMessage(), e);
        }
        log.info("Got token " + token);

        // Retrieve the ActivityDefinition using the canonical URL
        Bundle resultBundle = client
            .search()
            .forResource(ActivityDefinition.class)
            .where(ActivityDefinition.URL.matches().value(canonicalUrl))
            .withAdditionalHeader("Authorization", "Bearer " + token)
            .returnBundle(Bundle.class)
            .execute();

        if (resultBundle.getTotal() == 0) {
            throw new ActivityDefinitionNotFoundException("ActivityDefinition not found for canonical URL '" + canonicalUrl + "'");
        } else if (resultBundle.getTotal() > 1) {
            throw new ActivityDefinitionManyFoundException("Multiple ActivityDefinitions found for canonical URL '" + canonicalUrl + "'");
        } else {
            return (ActivityDefinition) resultBundle.getEntry().get(0).getResource();
        }

        
    }

    // Helper method to resolve the resource referenced by Task.focus
    public IBaseResource resolveFocusResource(Task task, IFhirRestServer fhirServer) {
        // Check if the resource is contained within the Task
        if (task.getFocus()!=null) {
            // Get the reference in the focus field
            String focusReference = task.getFocus().getReference();

            // If the reference starts with '#', it's a contained resource
            if (focusReference.startsWith("#")) {
                
                String containedId = focusReference.substring(1); // Remove the '#' prefix
                log.info("Focus reference is a contained resource with id " + containedId);
                // Find the contained resource with the matching ID

                for (Resource resource : task.getContained()) {
                    log.info(resource.getResourceType() + " " + resource.getId());
                }

                return task.getContained().stream()
                        .filter(resource -> resource.getId().equals(focusReference))
                        .findFirst()
                        .orElse(null);
            } else if (focusReference.contains("/")) {
                // If the reference starts with 'http', it's an external reference
                String[] parts = focusReference.split("/");
                return resolveExternalReference(parts[0], parts[1], fhirServer);
            }
        }

        // Otherwise, it's an external reference, which would require additional logic (not handled here)
        return null;
    }

    // Helper method to resolve the ActivityDefinition from a FHIR server
    public IBaseResource resolveExternalReference(String resource, String id, IFhirRestServer fhirServer) {
        // Define the base URL of the FHIR server where you expect to retrieve the resource
        String fhirServerBaseUrl = fhirServer.getEndpoint();

        // Create a FHIR client to communicate with the FHIR server
        IGenericClient client = fhirContext.newRestfulGenericClient(fhirServerBaseUrl);

        String token;
        try {
            token = fhirTokenService.getToken(fhirServer.getAuthenticationEndpoint(), fhirServer.getClientId(), fhirServer.getClientSecret(), false);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error fetching token " + e.getMessage(), e);
        }

        try {
            // Retrieve the ActivityDefinition using the canonical URL
            IBaseResource resolvedResource = client
                .read()
                .resource(resource)
                .withId(id)
                .withAdditionalHeader("Authorization", "Bearer " + token)
                .execute();

            return resolvedResource;
        } catch (Exception e) {
            // Handle any errors (e.g., resource not found or server error)
            log.error("Error fetching resource type " + resource + " with id '" + id + "'' " + e.getMessage());
            throw new RuntimeException("Error fetching resource type " + resource + " with id '" + id + "' " + e.getMessage(), e);
        }
    }

	private List<ExpressionConcept> parseExpressionConstraint(ExpressionConstraint expressionConstraint) {
		
		List<ExpressionConcept> foundConcepts = new ArrayList<>();
		
		if (expressionConstraint instanceof CompoundExpressionConstraint) {

			foundConcepts.addAll(parseCompoundExpressionConstraint((CompoundExpressionConstraint) expressionConstraint));
			
		} else if (expressionConstraint instanceof DottedExpressionConstraint) {

			foundConcepts.addAll(parseDottedExpressionConstraint((DottedExpressionConstraint) expressionConstraint));
			
		} else if (expressionConstraint instanceof SubExpressionConstraint) {

			foundConcepts.addAll(parseSubExpressionConstraint((SubExpressionConstraint) expressionConstraint));
			
		} else if (expressionConstraint instanceof RefinedExpressionConstraint) {

			foundConcepts.addAll(parseRefinedExpressionConstraint((RefinedExpressionConstraint) expressionConstraint));

		} else if (expressionConstraint instanceof ExpressionConcept) {
			
			foundConcepts.add(parseExpressionConcept((ExpressionConcept) expressionConstraint));
		} else {

			// This should never happen
			System.out.println("ExpressionConstraint is not an instance of any known class, it is an instance of: " + expressionConstraint.getClass().getName());
		}

		
		return foundConcepts;
	}
	private List<ExpressionConcept> parseCompoundExpressionConstraint(CompoundExpressionConstraint expressionConstraint) {
		List<ExpressionConcept> concepts = new ArrayList<>();

		if (expressionConstraint.getConjunctionExpressionConstraints() != null) {
			for (SubExpressionConstraint subExpressionConstraint : expressionConstraint.getConjunctionExpressionConstraints()) {
				concepts.addAll(parseSubExpressionConstraint(subExpressionConstraint));
			}
		}
		if (expressionConstraint.getDisjunctionExpressionConstraints() != null) {
			for (SubExpressionConstraint subExpressionConstraint : expressionConstraint.getDisjunctionExpressionConstraints()) {
				concepts.addAll(parseSubExpressionConstraint(subExpressionConstraint));
			}
		}
		if (expressionConstraint.getExclusionExpressionConstraints() != null) {
			Pair<SubExpressionConstraint> exclusionExpressionConstraints = expressionConstraint.getExclusionExpressionConstraints();
			if (exclusionExpressionConstraints != null) {
				if (exclusionExpressionConstraints.getFirst() != null) {
					concepts.addAll(parseSubExpressionConstraint(exclusionExpressionConstraints.getFirst()));
				}
				if (exclusionExpressionConstraints.getSecond() != null) {
					concepts.addAll(parseSubExpressionConstraint(exclusionExpressionConstraints.getSecond()));
				}
			}
		}
		return concepts;
	}	
	private List<ExpressionConcept> parseDottedExpressionConstraint(DottedExpressionConstraint constraint) {
		List<ExpressionConcept> concepts = new ArrayList<>();
		if (constraint.getSubExpressionConstraint() != null) {
			concepts.addAll(parseSubExpressionConstraint(constraint.getSubExpressionConstraint()));
		}
        if (constraint.getDottedAttributes() != null) {
            for(SubExpressionConstraint dottedAttribute : constraint.getDottedAttributes()) {
                concepts.addAll(parseSubExpressionConstraint(dottedAttribute));
            }
		}
		return concepts;
	}
	private List<ExpressionConcept> parseConceptFilterConstraints(ConceptFilterConstraint conceptFilterConstraint) {
		
		List<ExpressionConcept> foundConcepts = new ArrayList<>();
		
		if (conceptFilterConstraint.getDefinitionStatusFilters() != null) {
			for (FieldFilter fieldFilter : conceptFilterConstraint.getDefinitionStatusFilters()) {
				if (fieldFilter.getConceptReferences() != null) {
					for(ConceptReference conceptRef : fieldFilter.getConceptReferences()) {
						if (conceptRef.getConceptId() != null) {
							foundConcepts.add(new ExpressionConcept(conceptRef.getConceptId()));
						}
					}
				}
				if (fieldFilter.getSubExpressionConstraint() != null) {
					foundConcepts.addAll(parseSubExpressionConstraint(fieldFilter.getSubExpressionConstraint()));
				}
			}
		}
		return foundConcepts;
	}
    private List<ExpressionConcept> parseMemberFilterConstraints(MemberFilterConstraint memberFilterConstraint) {
		
		List<ExpressionConcept> foundConcepts = new ArrayList<>();
		
		if (memberFilterConstraint.getMemberFieldFilters() != null) {
			for (MemberFieldFilter fieldFilter : memberFilterConstraint.getMemberFieldFilters()) {
				if (fieldFilter.getSubExpressionConstraint() != null) {
					foundConcepts.addAll(parseSubExpressionConstraint(fieldFilter.getSubExpressionConstraint()));
				}
			}
		}
		return foundConcepts;
	}
	private List<ExpressionConcept> parseEclAttributeGroup(EclAttributeGroup eclAttributeGroup) {
		List<ExpressionConcept> concepts = new ArrayList<>();
		if (eclAttributeGroup.getAttributeSet() != null) {
			concepts.addAll(parseEclAttributeSet(eclAttributeGroup.getAttributeSet()));
		}
		return concepts;
	}
	private List<ExpressionConcept> parseEclSubAttributeSet(SubAttributeSet subAttributeSet) {
		List<ExpressionConcept> concepts = new ArrayList<>();
		if (subAttributeSet.getAttribute().getAttributeName() != null) {
			concepts.addAll(parseSubExpressionConstraint(subAttributeSet.getAttribute().getAttributeName()));
		}
		if (subAttributeSet.getAttribute().getValue() != null) {
			concepts.addAll(parseSubExpressionConstraint(subAttributeSet.getAttribute().getValue()));
		}
		if (subAttributeSet.getAttributeSet() != null) {
			concepts.addAll(parseEclAttributeSet(subAttributeSet.getAttributeSet()));
		}
		return concepts;
	}
	private List<ExpressionConcept> parseEclAttributeSet(EclAttributeSet eclAttributeSet) {
		List<ExpressionConcept> concepts = new ArrayList<>();
		
		if (eclAttributeSet.getSubAttributeSet() != null) {
			concepts.addAll(parseEclSubAttributeSet(eclAttributeSet.getSubAttributeSet()));
		}

		if (eclAttributeSet.getConjunctionAttributeSet() != null) {
			for (SubAttributeSet subAttributeSet : eclAttributeSet.getConjunctionAttributeSet()) {
				concepts.addAll(parseEclSubAttributeSet(subAttributeSet));
			}
		}

		if (eclAttributeSet.getDisjunctionAttributeSet() != null) {
			for (SubAttributeSet subAttributeSet : eclAttributeSet.getDisjunctionAttributeSet()) {
				concepts.addAll(parseEclSubAttributeSet(subAttributeSet));
			}
		}

		return concepts;
	}
	private List<ExpressionConcept> parseEclSubRefinement(SubRefinement subRefinement) {
		List<ExpressionConcept> concepts = new ArrayList<>();

		if (subRefinement.getEclRefinement() != null) {
			concepts.addAll(parseEclRefinement(subRefinement.getEclRefinement()));
		}

		if (subRefinement.getEclAttributeGroup()!=null) {
			concepts.addAll(parseEclAttributeGroup(subRefinement.getEclAttributeGroup()));

		}

		if (subRefinement.getEclAttributeSet() != null) {
			concepts.addAll(parseEclAttributeSet(subRefinement.getEclAttributeSet()));
		}

		return concepts;
	}
	private List<ExpressionConcept> parseEclRefinement(EclRefinement refinement) {
		List<ExpressionConcept> concepts = new ArrayList<>();
		
		if (refinement.getSubRefinement() != null) {
			concepts.addAll(parseEclSubRefinement(refinement.getSubRefinement()));
		}

		if (refinement.getConjunctionSubRefinements() != null) {
			for (SubRefinement subRefinement : refinement.getConjunctionSubRefinements()) {
				concepts.addAll(parseEclSubRefinement(subRefinement));
			}
		}
		if (refinement.getDisjunctionSubRefinements() != null) {
			for (SubRefinement subRefinement : refinement.getDisjunctionSubRefinements()) {
				concepts.addAll(parseEclSubRefinement(subRefinement));
			}
		}
		
		return concepts;
	}
	private List<ExpressionConcept> parseRefinedExpressionConstraint(RefinedExpressionConstraint constraint) {
		List<ExpressionConcept> concepts = new ArrayList<>();


		if (constraint.getEclRefinement() != null) {
			concepts.addAll(parseEclRefinement(constraint.getEclRefinement()));
		}
		

		if (constraint.getSubexpressionConstraint() != null) {
			concepts.addAll(parseExpressionConstraint(constraint.getSubexpressionConstraint()));
		}
		return concepts;
	}
	private List<ExpressionConcept> parseSubExpressionConstraint(SubExpressionConstraint constraint) {
		List<ExpressionConcept> concepts = new ArrayList<>();
		
		if (constraint.getConceptId() != null) {
			System.out.println("Concept ID in SubExpressionConstraint: " + constraint.getConceptId());
			concepts.add(new ExpressionConcept(constraint.getConceptId()));
		}
		
		if (constraint.getNestedExpressionConstraint() != null) {
			concepts.addAll(parseExpressionConstraint(constraint.getNestedExpressionConstraint()));
		}

        if (constraint.getMemberFilterConstraints() != null) {
            for (MemberFilterConstraint subConstraint : constraint.getMemberFilterConstraints()) {
                concepts.addAll(parseMemberFilterConstraints(subConstraint));
            }
        }

		if (constraint.getConceptFilterConstraints() != null) {
			for (ConceptFilterConstraint subConstraint : constraint.getConceptFilterConstraints()) {
				concepts.addAll(parseConceptFilterConstraints(subConstraint));
			}
		}

        if (constraint.getDescriptionFilterConstraints() != null) {
			for (DescriptionFilterConstraint subConstraint : constraint.getDescriptionFilterConstraints()) {
				if (subConstraint.getDialectFilters() != null) {
                    for (DialectFilter filter : subConstraint.getDialectFilters()) {
                        if (filter.getSubExpressionConstraint() != null) {
                            concepts.addAll(parseSubExpressionConstraint(filter.getSubExpressionConstraint()));
                        }
                    }
                }
                if (subConstraint.getModuleFilters() != null) {
                    for (FieldFilter filter : subConstraint.getModuleFilters()) {
                        if (filter.getSubExpressionConstraint() != null) {
                            concepts.addAll(parseSubExpressionConstraint(filter.getSubExpressionConstraint()));
                        }
                    }
                }

                if (subConstraint.getDescriptionTypeFilters() != null) {
                    for (DescriptionTypeFilter filter : subConstraint.getDescriptionTypeFilters()) {
                        if (filter.getSubExpressionConstraint() != null) {
                            concepts.addAll(parseSubExpressionConstraint(filter.getSubExpressionConstraint()));
                        }
                    }
                }
			}
		}

		return concepts;
	}
	private ExpressionConcept parseExpressionConcept(ExpressionConcept constraint) {
		
		if (constraint.getConceptId() != null) {
			System.out.println("Concept ID: " + constraint.getConceptId());
			return new ExpressionConcept(constraint.getConceptId());
		}
		return null;
	}
	

	public List<ExpressionConcept> eclParse(ExpressionConstraint expressionConstraint) {
		return parseExpressionConstraint(expressionConstraint);
	}

}
