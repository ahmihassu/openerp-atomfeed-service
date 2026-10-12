package org.bahmni.feed.openerp.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bahmni.feed.openerp.ObjectMapperRepository;
import org.bahmni.feed.openerp.OpenMRSPatientMapper;
import org.bahmni.feed.openerp.client.OpenMRSWebClient;
import org.bahmni.feed.openerp.domain.OpenMRSPatient;
import org.bahmni.openerp.web.client.OpenERPClient;
import org.bahmni.openerp.web.request.OpenERPRequest;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;

import java.io.IOException;
import java.net.URI;

public class OpenERPCustomerServiceEventWorker implements EventWorker {
    OpenERPClient openERPClient;
    private String feedUrl;
    private OpenMRSWebClient webClient;
    private String urlPrefix;
    private OpenERPCustomerRequestMapper customerRequestMapper;

    private static Logger logger = LogManager.getLogger(OpenERPCustomerServiceEventWorker.class);

    public OpenERPCustomerServiceEventWorker(String feedUrl, OpenERPClient openERPClient, OpenMRSWebClient webClient, String urlPrefix) {
        this.feedUrl = feedUrl;
        this.openERPClient = openERPClient;
        this.webClient = webClient;
        this.urlPrefix = urlPrefix;
        this.customerRequestMapper = new OpenERPCustomerRequestMapper();
    }

    @Override
    public void process(Event event) {
        try {
            openERPClient.execute(mapRequest(event));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cleanUp(Event event) {
    }

    private OpenERPRequest mapRequest(Event event) throws IOException {
        String content = event.getContent();
        String patientJSON = webClient.get(URI.create(urlPrefix + content));

        OpenMRSPatientMapper openMRSPatientMapper = new OpenMRSPatientMapper(ObjectMapperRepository.objectMapper);
        OpenMRSPatient openMRSPatient = openMRSPatientMapper.map(patientJSON);

        return customerRequestMapper.mapCreateCustomerRequest(
                openMRSPatient, event.getId(), event.getFeedUri(), feedUrl, event.getFeedUri() == null);
    }
}
