package org.openmrs.module.shrclient.handlers;


import org.apache.log4j.Logger;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.feeds.shr.DefaultEncounterFeedWorker;
import org.openmrs.module.shrclient.feeds.shr.ShrEncounterFeedProcessor;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.service.MciPatientService;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.StringUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.openmrs.module.fhir.utils.PropertyKeyConstants.FACILITY_ID;
import static org.openmrs.module.shrclient.util.Headers.ACCEPT_HEADER_KEY;
import static org.openmrs.module.shrclient.util.Headers.getHrmAccessTokenHeaders;
import static org.springframework.http.MediaType.APPLICATION_ATOM_XML;

public class EncounterPull {
    private final static String FACILITY_ID_HEADER_KEY = "facilityId";

    private final Logger logger = Logger.getLogger(EncounterPull.class);
    private ClientRegistry clientRegistry;

    public EncounterPull(ClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    public void download() throws IdentityUnauthorizedException {
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        ArrayList<String> encounterFeedUrls = getEncounterFeedUrls(propertiesReader);
        Map<String, String> requestHeaders = getRequestHeaders(propertiesReader);
        DefaultEncounterFeedWorker defaultEncounterFeedWorker = getEncounterFeedWorker();
        for (String encounterFeedUrl : encounterFeedUrls) {
            ShrEncounterFeedProcessor feedProcessor =
                    new ShrEncounterFeedProcessor(encounterFeedUrl, requestHeaders, defaultEncounterFeedWorker,
                            clientRegistry);
            try {
                feedProcessor.process();
            } catch (URISyntaxException e) {
                logger.error("Couldn't download catchment encounters. Error: ", e);
            }
        }
    }

    private DefaultEncounterFeedWorker getEncounterFeedWorker() {
        MciPatientService mciPatientService = PlatformUtil.getRegisteredComponent(MciPatientService.class);
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        IdentityStore identityStore = PlatformUtil.getIdentityStore();
        OMRSConceptLookup omrsConceptLookup = PlatformUtil.getOMRSConceptLookup();
        return new DefaultEncounterFeedWorker(mciPatientService, propertiesReader, identityStore, omrsConceptLookup);
    }

    private HashMap<String, String> getRequestHeaders(PropertiesReader propertiesReader) throws IdentityUnauthorizedException {
        HashMap<String, String> headers = new HashMap<>();
        Properties facilityInstanceProperties = propertiesReader.getFacilityInstanceProperties();
        headers.putAll(getHrmAccessTokenHeaders(clientRegistry.getOrCreateIdentityToken(), facilityInstanceProperties));
        headers.put(ACCEPT_HEADER_KEY, APPLICATION_ATOM_XML.toString());
        headers.put(FACILITY_ID_HEADER_KEY, getFacilityId(facilityInstanceProperties));
        return headers;
    }

    private String getFacilityId(Properties facilityInstanceProperties) {
        Object facilityId = facilityInstanceProperties.getProperty(FACILITY_ID);
        logger.info("Identified Facility:" + facilityId);
        if (facilityId == null) {
            throw new RuntimeException("Facility Id not defined.");
        }
        return (String) facilityId;
    }

    public ArrayList<String> getEncounterFeedUrls(PropertiesReader propertiesReader) {
        Properties facilityInstanceProperties = propertiesReader.getFacilityInstanceProperties();
        String shrBaseUrl = StringUtil.ensureSuffix(propertiesReader.getShrBaseUrl(), "/");
        String catchmentPathPattern = StringUtil.removePrefix(propertiesReader.getShrCatchmentPathPattern(), "/");
        String catchments = facilityInstanceProperties.get(PropertyKeyConstants.FACILITY_CATCHMENTS).toString();
        String[] facilityCatchments = catchments.split(",");
        ArrayList<String> catchmentsUrls = new ArrayList<>();
        for (String facilityCatchment : facilityCatchments) {
            String catchmentUrl = shrBaseUrl + String.format(catchmentPathPattern, facilityCatchment);
            //catchmentsUrls.add(String.format("%s/catchments/%s/encounters", shrBaseUrl, facilityCatchment));
            catchmentsUrls.add(catchmentUrl);
        }
        return catchmentsUrls;
    }

    public void retry() throws IdentityUnauthorizedException {
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        ArrayList<String> encounterFeedUrls = getEncounterFeedUrls(propertiesReader);

        Map<String, String> requestProperties = getRequestHeaders(propertiesReader);
        DefaultEncounterFeedWorker defaultEncounterFeedWorker = getEncounterFeedWorker();
        for (String encounterFeedUrl : encounterFeedUrls) {
            ShrEncounterFeedProcessor feedProcessor =
                    new ShrEncounterFeedProcessor(encounterFeedUrl, requestProperties, defaultEncounterFeedWorker,
                            clientRegistry);
            try {
                feedProcessor.processFailedEvents();
            } catch (URISyntaxException e) {
                logger.error("Couldn't download catchment encounters. Error: ", e);
            }
        }
    }
}
