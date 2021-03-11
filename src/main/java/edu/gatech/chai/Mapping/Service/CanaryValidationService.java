package edu.gatech.chai.Mapping.Service;

import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

@Service
public class CanaryValidationService {
	
	private RestTemplate restTemplate;
	@Value("${canary.url}")
	private String canaryURL;
	
	public CanaryValidationService() {
		this.restTemplate = new RestTemplate();
	}
	public JsonNode validateVRDRAgainstCanary(String VRDRJson) throws UnknownHostException, RestClientException {
		String POSTendpoint = canaryURL + "endpoints/record/1";
		String GETendpoint = canaryURL + "endpoints/get/1";
		System.out.println("Validating with canary at endpoint:" + POSTendpoint);
		JsonNode validationResults = JsonNodeFactory.instance.arrayNode();
		ResponseEntity<String> POSTresponse
		  = restTemplate.postForEntity(POSTendpoint, VRDRJson, String.class);
		if(POSTresponse.getStatusCode() != HttpStatus.OK) {
			return validationResults;
		}
		ResponseEntity<String> GETresponse
		  = restTemplate.getForEntity(GETendpoint, String.class);
		if(GETresponse.getStatusCode() != HttpStatus.OK) {
			return validationResults;
		}
		ObjectMapper mapper = new ObjectMapper();
		try {
			validationResults = mapper.readTree(GETresponse.getBody());
		} catch (IOException e) {
			return validationResults;
		}
		return validationResults.get("issues");
	}

	public String getCanaryURL() {
		return canaryURL;
	}

	public void setCanaryURL(String nightingaleURL) {
		this.canaryURL = nightingaleURL;
	}
}