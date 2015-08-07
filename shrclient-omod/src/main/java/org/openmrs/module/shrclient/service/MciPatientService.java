package org.openmrs.module.shrclient.service;

import org.openmrs.Concept;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.web.controller.dto.EncounterBundle;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface MciPatientService extends OpenmrsService {
    org.openmrs.Patient createOrUpdatePatient(Patient mciPatient);

    org.openmrs.PatientIdentifier generateIdentifier();

    void createOrUpdateEncounters(org.openmrs.Patient emrPatient, List<EncounterBundle> bundles, String healthId);

    void createOrUpdateEncounter(org.openmrs.Patient emrPatient, EncounterBundle encounterBundle, String healthId) throws Exception;

    Concept getCauseOfDeath(org.openmrs.Patient emrPatient);
}
