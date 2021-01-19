package edu.gatech.chai.Mapping.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.tomcat.util.codec.binary.Base64;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.UrlType;
import org.hl7.fhir.r4.model.MessageHeader.MessageDestinationComponent;
import org.hl7.fhir.r4.model.MessageHeader.MessageSourceComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.gatech.VRDR.context.VRDRFhirContext;
import edu.gatech.VRDR.model.DeathCertificateDocument;
import edu.gatech.chai.Mapping.Util.FHIRCMSToVRDRUtil;

@Service
public class VitalcheckSubmissionService{
	private RestTemplate restTemplate;
	private ObjectMapper objectMapper;
	@Autowired
	private VRDRFhirContext vrdrFhirContext;
	@Autowired
	public VitalcheckSubmissionService(VRDRFhirContext vrdrFhirContext) {
		restTemplate = new RestTemplate();
		//Cheat around to get resttemplate to parse the response token
		MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();
		mappingJackson2HttpMessageConverter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED));
		restTemplate.getMessageConverters().add(mappingJackson2HttpMessageConverter);
		objectMapper = new ObjectMapper();
		this.vrdrFhirContext = vrdrFhirContext;
	}
	
	public ResponseEntity<String> submitRecord(String POSTendpoint, DeathCertificateDocument dcd) throws RestClientException, IOException{
		//Setup basic authentication to get token from token server
		String staticVitalCheckTokenBearerEndpoint = "https://fhirtest.centralus.cloudapp.azure.com/IdentityServerNM/connect/token?daveuser=examiner&bucode=STATE_ME_OFFICE_OFFICE_OF_MEDICAL_INVESTIGATOR";
		String tokenAccessClientId = "client";
		String tokenAccessSecretId = "secret";
		String authString = tokenAccessClientId + ":" + tokenAccessSecretId;
		byte[] encodedAuth = Base64.encodeBase64(authString.getBytes(Charset.forName("US-ASCII")));
		String authHeader = "Basic " + new String(encodedAuth);
		//Setup request headers for token auth
		HttpHeaders headers = new HttpHeaders();
		headers.setCacheControl(CacheControl.noCache());
		headers.setBasicAuth(tokenAccessClientId, tokenAccessSecretId);
		headers.setAccept(Collections.singletonList(MediaType.ALL));
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		//Setup RequestBody
		String requestBodyString = "grant_type=client_credentials&scope=fhir:read%20fhir:write";
		HttpEntity<String> entity = new HttpEntity<String>(requestBodyString, headers);
		//Request
		ResponseEntity<String> tokenResponseEntity = null;
		tokenResponseEntity = restTemplate.postForEntity(staticVitalCheckTokenBearerEndpoint, entity, String.class);
		String tokenResponse = tokenResponseEntity.getBody();
		//Read token
		JsonNode tokenResponseJson = objectMapper.readTree(tokenResponse);
		String accessToken = tokenResponseJson.get("access_token").asText();
		//Setup post for new record. Note have to use MVM because the httpheaders class doesn't allow custom content type
		MultiValueMap<String, String> headerStrings = new LinkedMultiValueMap<String, String>();
		headerStrings.add("Authorization", "Bearer " + accessToken);
		headerStrings.add("Content-Type", "application/fhir+xml");
		//Create messageheader and bundle it with the dcd
		Bundle messageBundle = new Bundle();
		
		Parameters parameters = new Parameters();
		parameters.setId(new IdType(UUID.randomUUID().toString()));
		Identifier localPatientIdentifier = FHIRCMSToVRDRUtil.findLocalPatientIdentifierInDCD(dcd);
		parameters.addParameter(new ParametersParameterComponent().setName("external-case-id").setValue(localPatientIdentifier.getValueElement()));
		
		MessageHeader header = new MessageHeader();
		header.setEvent(new UriType("http://vitalchek.org/Send_New_Death_Case_To_DAVE"));
		header.addDestination(new MessageDestinationComponent(new UrlType("https://fhirtest.centralus.cloudapp.azure.com/summerNM/")));
		header.setSource(new MessageSourceComponent(new UrlType("https://apps.hdap.gatech.edu/ForteFhirMapper/")));
		header.addFocus(new Reference(parameters.getId()));
		header.addFocus(new Reference(dcd.getId()));
		
		messageBundle.addEntry().setResource(header);
		messageBundle.addEntry().setResource(parameters);
		messageBundle.addEntry().setResource(dcd);
		String bundleMessageXML = vrdrFhirContext.getCtx().newXmlParser().encodeResourceToString(messageBundle);
		System.out.println(bundleMessageXML);
		//TODO: Submit messageheader bundle to vitalcheck
		HttpEntity<String> directRequestEntity = new HttpEntity<String>(bundleMessageXML, headerStrings);
		ResponseEntity<String> response = restTemplate.postForEntity(POSTendpoint, directRequestEntity, String.class);
		return response;
	}
}