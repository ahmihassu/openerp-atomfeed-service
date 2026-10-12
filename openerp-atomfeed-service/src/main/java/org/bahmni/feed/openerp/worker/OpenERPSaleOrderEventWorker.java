package org.bahmni.feed.openerp.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bahmni.feed.openerp.ObjectMapperRepository;
import org.bahmni.feed.openerp.OpenMRSPatientMapper;
import org.bahmni.feed.openerp.client.OpenMRSWebClient;
import org.bahmni.feed.openerp.domain.OpenMRSPatient;
import org.bahmni.feed.openerp.domain.encounter.MapERPOrders;
import org.bahmni.feed.openerp.domain.encounter.OpenMRSEncounter;
import org.bahmni.feed.openerp.domain.visit.OpenMRSVisit;
import org.bahmni.openerp.web.client.OpenERPClient;
import org.bahmni.openerp.web.request.OpenERPRequest;
import org.bahmni.openerp.web.request.builder.Parameter;
import org.bahmni.openerp.web.service.CustomerService;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;

import java.io.IOException;
import java.net.URI;

public class OpenERPSaleOrderEventWorker implements EventWorker {
    OpenERPClient openERPClient;
    private String feedUrl;
    private OpenMRSWebClient webClient;
    private String urlPrefix;
    private CustomerService customerService;
    private OpenERPCustomerRequestMapper customerRequestMapper;

    private static Logger logger = LogManager.getLogger(OpenERPSaleOrderEventWorker.class);

    public OpenERPSaleOrderEventWorker(String feedUrl, OpenERPClient openERPClient, OpenMRSWebClient webClient, String urlPrefix) {
        this(feedUrl, openERPClient, webClient, urlPrefix, new CustomerService(openERPClient));
    }

    OpenERPSaleOrderEventWorker(String feedUrl, OpenERPClient openERPClient, OpenMRSWebClient webClient, String urlPrefix,
                                CustomerService customerService) {
        this.feedUrl = feedUrl;
        this.openERPClient = openERPClient;
        this.webClient = webClient;
        this.urlPrefix = urlPrefix;
        this.customerService = customerService;
        this.customerRequestMapper = new OpenERPCustomerRequestMapper();
    }

    @Override
    public void process(Event event) {
        try {
            OpenERPRequest openERPRequest = mapRequest(event);
            if (!openERPRequest.shouldERPConsumeEvent())
                return;

            openERPClient.execute(openERPRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cleanUp(Event event) {
    }

    private OpenERPRequest mapRequest(Event event) throws IOException {

        String encounterEventContent = webClient.get(URI.create(urlPrefix + event.getContent()));
        OpenMRSEncounter openMRSEncounter = ObjectMapperRepository.objectMapper.readValue(encounterEventContent, OpenMRSEncounter.class);

        // Ignore Bed Assignment Encounter events
        if(!openMRSEncounter.shouldERPConsumeEvent())
            return OpenERPRequest.DO_NOT_CONSUME;

        ensureCustomerExistsInOdoo(openMRSEncounter, event);

        String visitURL = "/openmrs/ws/rest/v1/visit/" + openMRSEncounter.getVisitUuid() + "?v=full";
        String visitContent = webClient.get(URI.create(urlPrefix + visitURL));

        OpenMRSVisit openMRSVisit = ObjectMapperRepository.objectMapper.readValue(visitContent, OpenMRSVisit.class);
        MapERPOrders mapERPOrders = new MapERPOrders(openMRSEncounter, openMRSVisit);

        OpenERPRequest erpRequest = new OpenERPRequest("atom.event.worker", "process_event", mapERPOrders.getParameters(event.getId(), event.getFeedUri(), feedUrl));
        if (event.getFeedUri() == null)
            erpRequest.addParameter(createParameter("is_failed_event", "1", "boolean"));

        return erpRequest;
    }

    private void ensureCustomerExistsInOdoo(OpenMRSEncounter openMRSEncounter, Event event) throws IOException {
        String patientId = openMRSEncounter.getPatientId();
        if (patientId == null || customerExists(patientId)) {
            return;
        }

        logger.info("Patient {} not found in Odoo. Creating customer before syncing encounter {}",
                patientId, openMRSEncounter.getEncounterUuid());

        String patientURL = "/openmrs/ws/rest/v1/patient/" + openMRSEncounter.getPatientUuid() + "?v=full";
        String patientJSON = webClient.get(URI.create(urlPrefix + patientURL));
        OpenMRSPatient openMRSPatient = new OpenMRSPatientMapper(ObjectMapperRepository.objectMapper).map(patientJSON);

        OpenERPRequest createCustomerRequest = customerRequestMapper.mapCreateCustomerRequest(
                openMRSPatient, event.getId(), event.getFeedUri(), feedUrl, event.getFeedUri() == null);
        openERPClient.execute(createCustomerRequest);
    }

    private boolean customerExists(String patientId) {
        Object[] customers = customerService.findCustomersWithPatientReference(patientId);
        return customers != null && customers.length > 0;
    }

    private Parameter createParameter(String name, String value, String type) {
        return new Parameter(name, value, type);
    }
}
