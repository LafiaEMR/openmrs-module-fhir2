/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.translators.impl;

import javax.inject.Inject;

import lombok.AccessLevel;
import lombok.Setter;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.DiagnosticReportTranslator;
import org.openmrs.module.fhir2.api.translators.EncounterReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.ObservationReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.PatientReferenceTranslator;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class DiagnosticReportTranslatorImpl implements DiagnosticReportTranslator {
	
	@Inject
	private EncounterReferenceTranslator encounterReferenceTranslator;
	
	@Inject
	private PatientReferenceTranslator patientReferenceTranslator;
	
	@Inject
	private ConceptTranslator conceptTranslator;
	
	@Inject
	private ObservationReferenceTranslator observationReferenceTranslator;
	
	@Override
	public DiagnosticReport toFhirResource(Obs obsGroup) {
		if (obsGroup == null)
			return null;
		
		if (!obsGroup.isObsGrouping()) {
			throw new IllegalArgumentException("Obs object must be an Obs group.");
		}
		
		DiagnosticReport diagnosticReport = new DiagnosticReport();
		
		setFhirFields(obsGroup, diagnosticReport);
		
		return diagnosticReport;
	}
	
	private void setFhirFields(Obs obsGroup, DiagnosticReport diagnosticReport) {
		// DiagnosticReport.id
		diagnosticReport.setId(obsGroup.getUuid());
		
		// DiagnosticReport.status
		diagnosticReport.setStatus(DiagnosticReport.DiagnosticReportStatus.UNKNOWN);
		
		// DiagnosticReport.encounter
		Encounter encounter = obsGroup.getEncounter();
		if (encounter != null) {
			diagnosticReport.setEncounter(encounterReferenceTranslator.toFhirResource(encounter));
		}
		
		// DiagnosticReport.subject
		Person subject = obsGroup.getPerson();
		if (subject != null) {
			try {
				diagnosticReport.setSubject(patientReferenceTranslator.toFhirResource((Patient) subject));
			}
			catch (ClassCastException ignored) {}
		}
		
		// DiagnosticReport.code
		Concept code = obsGroup.getConcept();
		if (code != null) {
			diagnosticReport.setCode(conceptTranslator.toFhirResource(code));
		}
		
		// DiagnosticReport.category
		diagnosticReport.addCategory().addCoding().setSystem(FhirConstants.DIAGNOSTIC_SERVICE_SECTIONS_VALUESET_URI)
		        .setCode(FhirConstants.DIAGNOSTIC_REPORT_CATEGORY_LAB);
		
		// DiagnosticReport.issued
		diagnosticReport.setIssued(obsGroup.getDateCreated());
		
		// DiagnosticReport.result
		for (Obs obs : obsGroup.getGroupMembers()) {
			diagnosticReport.addResult(observationReferenceTranslator.toFhirResource(obs));
		}
	}
}
