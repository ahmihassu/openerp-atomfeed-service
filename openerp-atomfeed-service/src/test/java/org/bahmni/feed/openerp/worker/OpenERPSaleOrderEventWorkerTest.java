package org.bahmni.feed.openerp.worker;

import org.bahmni.feed.openerp.client.OpenMRSWebClient;
import org.bahmni.feed.openerp.testhelper.FileConverter;
import org.bahmni.openerp.web.client.OpenERPClient;
import org.bahmni.openerp.web.request.OpenERPRequest;
import org.bahmni.openerp.web.request.builder.Parameter;
import org.bahmni.openerp.web.service.CustomerService;
import org.ict4h.atomfeed.client.domain.Event;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OpenERPSaleOrderEventWorkerTest {

    private static final String FEED_URL = "http://openmrs/encounter/feed";
    private static final String URL_PREFIX = "http://mrs.auth.uri";
    private static final String PATIENT_ID = "GAN200039";

    @Mock
    private OpenERPClient openERPClient;
    @Mock
    private OpenMRSWebClient webClient;
    @Mock
    private CustomerService customerService;
    @Mock
    private Event event;

    private OpenERPSaleOrderEventWorker eventWorker;
    private String encounterResource;
    private String patientResource;
    private String visitResource;

    @Before
    public void setUp() {
        eventWorker = new OpenERPSaleOrderEventWorker(FEED_URL, openERPClient, webClient, URL_PREFIX, customerService);
        encounterResource = FileConverter.convertToString("encounterResourceForDrugOrder.json");
        patientResource = FileConverter.convertToString("patientResource.xml");
        visitResource = "{\"uuid\":\"5de144d7-554b-45f9-9201-4e35f1f21bea\",\"attributes\":[]}";

        when(event.getContent()).thenReturn("/openmrs/ws/rest/v1/bahmniencounter/52619b81-d4a2-467a-b407-fbb316b64df8");
        when(event.getId()).thenReturn("event-1");
        when(event.getFeedUri()).thenReturn(FEED_URL);
        when(webClient.get(any(URI.class))).thenAnswer(invocation -> {
            URI uri = (URI) invocation.getArguments()[0];
            String path = uri.toString();
            if (path.contains("/patient/")) {
                return patientResource;
            }
            if (path.contains("/visit/")) {
                return visitResource;
            }
            return encounterResource;
        });
    }

    @Test
    public void shouldCreateCustomerInOdooWhenMissingBeforeSyncingSaleOrder() {
        when(customerService.findCustomersWithPatientReference(PATIENT_ID)).thenReturn(new Object[0]);

        eventWorker.process(event);

        ArgumentCaptor<OpenERPRequest> requestCaptor = ArgumentCaptor.forClass(OpenERPRequest.class);
        verify(openERPClient, times(2)).execute(requestCaptor.capture());

        List<OpenERPRequest> requests = requestCaptor.getAllValues();
        assertEquals("create.customer", getCategory(requests.get(0)));
        assertEquals("create.sale.order", getCategory(requests.get(1)));
    }

    @Test
    public void shouldNotCreateCustomerWhenAlreadyPresentInOdoo() {
        when(customerService.findCustomersWithPatientReference(PATIENT_ID)).thenReturn(new Object[]{123});

        eventWorker.process(event);

        ArgumentCaptor<OpenERPRequest> requestCaptor = ArgumentCaptor.forClass(OpenERPRequest.class);
        verify(openERPClient, times(1)).execute(requestCaptor.capture());
        assertEquals("create.sale.order", getCategory(requestCaptor.getValue()));
        verify(webClient, never()).get(URI.create(URL_PREFIX + "/openmrs/ws/rest/v1/patient/f0e4b703-5bd7-450a-97ed-828384561a2b?v=full"));
    }

    @Test
    public void shouldSkipProcessingWhenEncounterHasNoOrders() {
        doReturn(FileConverter.convertToString("admissionEncounterResourceWithoutOrders.json"))
                .when(webClient).get(any(URI.class));

        eventWorker.process(event);

        verify(customerService, never()).findCustomersWithPatientReference(anyString());
        verify(openERPClient, never()).execute(any(OpenERPRequest.class));
    }

    private String getCategory(OpenERPRequest request) {
        for (Parameter parameter : request.getParameters()) {
            if ("category".equals(parameter.getName())) {
                return parameter.getValue();
            }
        }
        throw new AssertionError("category parameter not found in request: " + request);
    }
}
