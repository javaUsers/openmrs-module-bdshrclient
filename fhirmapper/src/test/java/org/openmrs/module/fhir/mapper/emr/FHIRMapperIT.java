package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.dstu2.resource.Bundle;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PatientService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.Constants.UNVERIFIED_BY_TR;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private FHIRMapper fhirMapper;

    @Autowired
    private PatientService patientService;

    @Autowired
    private ConceptService conceptService;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapObservations() throws Exception {
        executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
        Bundle encounterBundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithObservations.xml", springContext);

        Patient patient = patientService.getPatient(1);

        Encounter encounter = fhirMapper.map(patient, "98101039678", "shr_enc_id", encounterBundle, getSystemProperties("1"));
        assertEquals(4, encounter.getAllObs().size());

        Set<Obs> topLevelObs = encounter.getObsAtTopLevel(false);
        assertEquals(1, topLevelObs.size());

        Obs vitalsObs = topLevelObs.iterator().next();
        Concept vitalsConcept = conceptService.getConceptByName("Vitals" + UNVERIFIED_BY_TR);
        assertEquals(vitalsConcept, vitalsObs.getConcept());

        assertEquals(2, vitalsObs.getGroupMembers().size());

        Obs pulseObs = identifyObsByConcept(vitalsObs.getGroupMembers(), conceptService.getConcept(303));
        assertTrue(75 == pulseObs.getValueNumeric());
        Concept bpConcept = conceptService.getConceptByName("Blood Pressure" + UNVERIFIED_BY_TR);
        Obs bpObs = identifyObsByConcept(vitalsObs.getGroupMembers(), bpConcept);
        assertEquals(1, bpObs.getGroupMembers().size());
        Obs diastolicObs = identifyObsByConcept(bpObs.getGroupMembers(), conceptService.getConcept(305));
        assertTrue(70 == diastolicObs.getValueNumeric());

    }

    @Test
    public void shouldCreateConceptsWhenNeeded() throws Exception {
        executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
        Bundle encounterBundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithLocalConceptsVitals.xml", springContext);

        Patient patient = patientService.getPatient(1);

        Encounter encounter = fhirMapper.map(patient, "98101039678", "shr-enc-id", encounterBundle, getSystemProperties("1"));
        assertEquals(6, encounter.getAllObs().size());

        Set<Obs> topLevelObs = encounter.getObsAtTopLevel(false);
        assertEquals(1, topLevelObs.size());

        Obs vitalsObs = topLevelObs.iterator().next();
        Concept vitalsConcept = conceptService.getConceptByName("Vitals" + UNVERIFIED_BY_TR);
        assertEquals(vitalsConcept, vitalsObs.getConcept());

        assertEquals(3, vitalsObs.getGroupMembers().size());

        Obs pulseObs = identifyObsByConcept(vitalsObs.getGroupMembers(), conceptService.getConcept(303));
        assertTrue(75 == pulseObs.getValueNumeric());

        Concept temperatureConcept = conceptService.getConceptByName("Temperature" + UNVERIFIED_BY_TR);
        Collection<ConceptName> shortNames = temperatureConcept.getShortNames();
        assertFalse(shortNames.isEmpty());
        assertEquals(1, shortNames.size());
        assertEquals("Temperature", shortNames.iterator().next().getName());
        Obs temperatureObs = identifyObsByConcept(vitalsObs.getGroupMembers(), temperatureConcept);
        assertEquals("97.0 Deg F", temperatureObs.getValueAsString(Locale.ENGLISH));

        Concept bpConcept = conceptService.getConceptByName("Blood Pressure" + UNVERIFIED_BY_TR);
        assertEquals("Blood Pressure", bpConcept.getShortNames().iterator().next().getName());
        Obs bpObs = identifyObsByConcept(vitalsObs.getGroupMembers(), bpConcept);
        assertEquals(2, bpObs.getGroupMembers().size());

        Obs diastolicObs = identifyObsByConcept(bpObs.getGroupMembers(), conceptService.getConcept(305));
        assertTrue(70 == diastolicObs.getValueNumeric());

        Concept sbpConcept = conceptService.getConceptByName("Systolic Blood Pressure" + UNVERIFIED_BY_TR);
        Obs sbpObs = identifyObsByConcept(bpObs.getGroupMembers(), sbpConcept);
        assertEquals("110.0", sbpObs.getValueAsString(Locale.ENGLISH));
    }

    @Test(expected = RuntimeException.class)
    public void shouldFailToMapWhenATrConceptIsNotSynced() throws Exception {
        executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
        Bundle encounterBundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithNotSyncedTRConcepts.xml", springContext);

        Patient patient = patientService.getPatient(1);
        fhirMapper.map(patient, "98101039678", "shr-enc-id", encounterBundle, getSystemProperties("1"));
    }

    @Test
    public void shouldCreateRootConceptsAndItsChildrenWhenNeeded() throws Exception {
        executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
        Bundle encounterBundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithLocalConceptsInObservation.xml", springContext);

        String conceptNameHeight = "Height";
        String conceptNameWeight = "Weight";
        String conceptNameHeightAndWeight = "Height and Weight";

        assertNull(conceptService.getConceptByName(conceptNameHeight));
        assertNull(conceptService.getConceptByName(conceptNameWeight));
        assertNull(conceptService.getConceptByName(conceptNameHeightAndWeight));

        Patient patient = patientService.getPatient(1);
        Encounter emrEncounter = fhirMapper.map(patient, "98101039678", "shr-enc-id", encounterBundle, getSystemProperties("1"));

        assertEquals(3, emrEncounter.getAllObs().size());
        final Set<Obs> topLevelObs = emrEncounter.getObsAtTopLevel(false);
        assertEquals(1, topLevelObs.size());

        Obs heightAndWeight = topLevelObs.iterator().next();
        Set<Obs> children = heightAndWeight.getGroupMembers();
        assertEquals(2, children.size());

        Concept height = conceptService.getConceptByName(conceptNameHeight);
        assertCreatedConcept(height, conceptNameHeight);

        Concept weight = conceptService.getConceptByName(conceptNameWeight);
        assertCreatedConcept(weight, conceptNameWeight);

        Concept heightAndWeightConcept = conceptService.getConceptByName(conceptNameHeightAndWeight);
        assertCreatedConcept(heightAndWeightConcept, conceptNameHeightAndWeight);

        Obs heightObs = identifyObsByConcept(children, height);
        assertEquals("6.0 ft", heightObs.getValueAsString(Locale.ENGLISH));

        Obs weightObs = identifyObsByConcept(children, weight);
        assertEquals("60.0 Kg", weightObs.getValueAsString(Locale.ENGLISH));
    }

    @Test
    public void shouldCreateAConceptForReferenceTermWhenNeeded() throws Exception {
        executeDataSet("testDataSets/shrObservationsWithReferenceTerms.xml");
        Bundle encounterBundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithLocalConceptAndReferenceTerms.xml", springContext);

        Patient patient = patientService.getPatient(1);

        Encounter encounter = fhirMapper.map(patient, "98101039678", "shr-enc-id", encounterBundle, getSystemProperties("1"));
        assertEquals(6, encounter.getAllObs().size());

        Set<Obs> topLevelObs = encounter.getObsAtTopLevel(false);
        assertEquals(1, topLevelObs.size());

        Obs vitalsObs = topLevelObs.iterator().next();
        Concept vitalsConcept = conceptService.getConceptByName("Vitals" + UNVERIFIED_BY_TR);
        assertEquals(vitalsConcept, vitalsObs.getConcept());

        assertEquals(3, vitalsObs.getGroupMembers().size());

        Obs pulseObs = identifyObsByConcept(vitalsObs.getGroupMembers(), conceptService.getConcept(303));
        assertTrue(75 == pulseObs.getValueNumeric());

        Concept temperatureConcept = conceptService.getConceptByName("Temperature" + UNVERIFIED_BY_TR);
        Obs temperatureObs = identifyObsByConcept(vitalsObs.getGroupMembers(), temperatureConcept);
        assertEquals("97.0 Deg F", temperatureObs.getValueAsString(Locale.ENGLISH));

        Concept bpConcept = conceptService.getConceptByName("Blood Pressure" + UNVERIFIED_BY_TR);
        Obs bpObs = identifyObsByConcept(vitalsObs.getGroupMembers(), bpConcept);
        assertEquals(2, bpObs.getGroupMembers().size());

        Obs diastolicObs = identifyObsByConcept(bpObs.getGroupMembers(), conceptService.getConcept(305));
        assertTrue(70 == diastolicObs.getValueNumeric());

        Concept sbpConcept = conceptService.getConceptByName("Systolic Blood Pressure" + UNVERIFIED_BY_TR);
        Obs sbpObs = identifyObsByConcept(bpObs.getGroupMembers(), sbpConcept);
        assertEquals("110.0", sbpObs.getValueAsString(Locale.ENGLISH));
    }

    private void assertCreatedConcept(Concept concept, String expectedShortName) {
        assertNotNull(concept);
        assertEquals(expectedShortName + UNVERIFIED_BY_TR, concept.getFullySpecifiedName(Locale.ENGLISH).getName());
        assertEquals("DLN-H10018686", concept.getVersion());
        assertEquals(concept.getConceptClass(), conceptService.getConceptClassByUuid(ConceptClass.MISC_UUID));
        assertTrue(concept.getDatatype().isText());
    }

    private Obs identifyObsByConcept(Set<Obs> obses, Concept concept) {
        for (Obs obs : obses) {
            if (obs.getConcept().equals(concept)) {
                return obs;
            }
        }
        return null;
    }
}