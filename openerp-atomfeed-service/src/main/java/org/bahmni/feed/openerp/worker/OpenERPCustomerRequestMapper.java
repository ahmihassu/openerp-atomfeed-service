package org.bahmni.feed.openerp.worker;

import org.apache.commons.lang3.StringUtils;
import org.bahmni.feed.openerp.domain.OpenMRSPatient;
import org.bahmni.feed.openerp.domain.OpenMRSPatientIdentifier;
import org.bahmni.feed.openerp.domain.OpenMRSPerson;
import org.bahmni.feed.openerp.domain.OpenMRSPersonAddress;
import org.bahmni.openerp.web.request.OpenERPRequest;
import org.bahmni.openerp.web.request.builder.Parameter;

import java.util.ArrayList;
import java.util.List;

public class OpenERPCustomerRequestMapper {

    public OpenERPRequest mapCreateCustomerRequest(OpenMRSPatient openMRSPatient, String eventId,
                                                   String feedUri, String feedUrl, boolean isFailedEvent) {
        return new OpenERPRequest("atom.event.worker", "process_event",
                mapParameters(openMRSPatient, eventId, feedUri, feedUrl, isFailedEvent));
    }

    public List<Parameter> mapParameters(OpenMRSPatient openMRSPatient, String eventId, String feedUri,
                                         String feedUrl, boolean isFailedEvent) {
        List<Parameter> parameters = new ArrayList<Parameter>();
        parameters.add(createParameter("name", openMRSPatient.getName(), "string"));
        parameters.add(createParameter("local_name", openMRSPatient.getLocalName(), "string"));
        parameters.add(createParameter("ref", getPrimaryIdentifier(openMRSPatient).getIdentifier(), "string"));
        parameters.add(createParameter("uuid", openMRSPatient.getUuid(), "string"));
        String village = identifyVillage(openMRSPatient);
        if (!StringUtils.isBlank(village)) {
            parameters.add(createParameter("village", village, "string"));
        }
        OpenMRSPerson person = openMRSPatient.getPerson();
        if (person.getGender() != null) {
            parameters.add(createParameter("gender", person.getGender(), "string"));
        }
        if (person.getBirthdate() != null) {
            parameters.add(createParameter("birthdate", formatBirthdate(person.getBirthdate()), "string"));
        }
        if (person.getAttributes() != null) {
            parameters.add(createParameter("attributes", person.getAttributes().toJsonString(), "string"));
        } else {
            parameters.add(createParameter("attributes", null, "string"));
        }
        if (person.getPreferredAddress() != null) {
            parameters.add(createParameter("preferredAddress", person.getPreferredAddress().toJsonString(), "string"));
        } else {
            parameters.add(createParameter("preferredAddress", "{}", "string"));
        }

        parameters.add(createParameter("category", "create.customer", "string"));
        if ((feedUrl != null && feedUrl.contains("$param.value")) || (feedUri != null && feedUri.contains("$param.value")))
            throw new RuntimeException("Junk values in the feedUrl:$param.value");
        parameters.add(createParameter("feed_uri", feedUrl, "string"));
        parameters.add(createParameter("last_read_entry_id", eventId, "string"));
        parameters.add(createParameter("feed_uri_for_last_read_entry", feedUri, "string"));
        if (isFailedEvent)
            parameters.add(createParameter("is_failed_event", "1", "boolean"));
        return parameters;
    }

    private OpenMRSPatientIdentifier getPrimaryIdentifier(OpenMRSPatient patient) {
        for (OpenMRSPatientIdentifier identifier : patient.getIdentifiers()) {
            if (identifier.isPreferred()) {
                return identifier;
            }
        }
        throw new RuntimeException("Preferred or Primary identifier is not available for the patient: " + patient.getName());
    }

    private String identifyVillage(OpenMRSPatient openMRSPatient) {
        OpenMRSPersonAddress preferredAddress = openMRSPatient.getPerson().getPreferredAddress();
        return (preferredAddress != null) ? preferredAddress.getCityVillage() : null;
    }

    private String formatBirthdate(String birthdate) {
        return birthdate.length() >= 10 ? birthdate.substring(0, 10) : birthdate;
    }

    private Parameter createParameter(String name, String value, String type) {
        return new Parameter(name, value, type);
    }
}
