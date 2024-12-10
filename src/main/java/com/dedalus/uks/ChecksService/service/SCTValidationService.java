package com.dedalus.uks.ChecksService.service;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dedalus.uks.ChecksService.utils.Utilities;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

@Service
@Slf4j
public class SCTValidationService {


    public static final String INVALID_CODE_DETAIL_ERROR_CODE = "INVALID_CONCEPT_IDENTIFIER_FORMAT";
    public static final String INVALID_CODE_DETAIL_ERROR_DESC = "Concept is not in the correct format for the CodeSystem";

    @Autowired
    public SCTValidationService() {

    }

    public OperationOutcome validateIdentifierFormat(String sctIdentifier) {
        log.debug("Validating SCI Identifier: " + sctIdentifier);
        OperationOutcome operationOutcome = new OperationOutcome();

        if (sctIdentifier == null || sctIdentifier.isEmpty()) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier is empty");
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }
        
        if (!validateSnomedCTIdentifier(sctIdentifier)) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " is an invalid SCT identifier format");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is not a valid SNOMED CT Concept ID.");
            issue.setDetails(issueDetail);
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }


        if (sctIdentifier.length() < 6) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " too short. It has a length of " + + sctIdentifier.length() + " characters, and the minumum allowed is 6.");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is too short.");
            issue.setDetails(issueDetail);
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        if (sctIdentifier.length() > 18) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " too long. It has a length of " + + sctIdentifier.length() + " characters, and the maximum allowed is 18.");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is too long.");
            issue.setDetails(issueDetail);
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        String partitionIdentifier = sctIdentifier.charAt(sctIdentifier.length() - 3) + "" + sctIdentifier.charAt(sctIdentifier.length() - 2);

        boolean longForm = false;
        if (partitionIdentifier.equals("10")) {
            longForm = true;
        }

        if (partitionIdentifier.equals("01") || partitionIdentifier.equals("11")) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " is an SCT Description identifier format");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is not a valid SNOMED CT Concept ID. The format indicates it is a Description.");
            issue.setDetails(issueDetail);
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        if (partitionIdentifier.equals("02") || partitionIdentifier.equals("12")) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " is an SCT Relationship format");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is not a valid SNOMED CT Concept ID. The format indicates it is a SCT Relationship identifier.");
            issue.setDetails(issueDetail);
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        if (partitionIdentifier.equals("16")) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " is an SCT Postcoordinated Expression format");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is not a valid SNOMED CT Concept ID. The format indicates it is a SCT Postcoordinated Expression identifier.");
            issue.setDetails(issueDetail);
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        if (longForm && sctIdentifier.length() < 11) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " is a 'long-form' identifier. It has a length of " + + sctIdentifier.length() + " characters, and the minumum allowed is 11.");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is too short for a long-form identifier.");
            issue.setDetails(issueDetail);
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }

        if (!partitionIdentifier.equals("00") && !partitionIdentifier.equals("10")) {
            OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setCode(IssueType.CODEINVALID);
            issue.setDiagnostics("Identifier " + sctIdentifier + " has an invalid SNOMED CT Concept identifier format");
            CodeableConcept issueDetail = new CodeableConcept();
            Coding issueDetailCode = new Coding();
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setSystem(Utilities.ISSUE_DETAIL_SYSTEM_URI);
            issueDetailCode.setCode(INVALID_CODE_DETAIL_ERROR_CODE);
            issueDetailCode.setDisplay(INVALID_CODE_DETAIL_ERROR_DESC);
            issueDetail.addCoding(issueDetailCode);
            issueDetail.setText("The provided identifier is not a valid SNOMED CT Concept ID.");
            issue.setDetails(issueDetail);
            
            operationOutcome.addIssue(issue);
            return operationOutcome;
        }
        

        return operationOutcome;
    }

    // Verhoeff Dihedral table
    private final int[][] d = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
        {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
        {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
        {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
        {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
        {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
        {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
        {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
        {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    // Permutation table
    private final int[][] p = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
        {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
        {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
        {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
        {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
        {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
        {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    private boolean validateSnomedCTIdentifier(String sctid) {
        
        String sctidPattern = "^[0-9]+$";
        
        // First, check if the identifier is numeric and not empty
        if (!sctid.matches(sctidPattern)) {
            log.info(sctidPattern + " does not match SCT ID pattern " + sctidPattern);
            return false;
        }

        // Apply Verhoeff algorithm for validation
        int check = 0;
        int len = sctid.length();
        for (int i = 0; i < len; i++) {
            check = d[check][p[(i % 8)][Character.getNumericValue(sctid.charAt(len - i - 1))]];
        }

        if (check != 0) {
            log.info("SCT ID " + sctid + " failed Verhoeff check");
        }
        
        return check == 0;
    }

}
