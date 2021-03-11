package edu.gatech.chai.Mapping.Service;

import java.io.IOException;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class NightingaleSubmissionService {

	private RestTemplate restTemplate;
	
	public NightingaleSubmissionService() {
		this.restTemplate = new RestTemplate();
	}
	
	public ResponseEntity<String> submitRecord(String POSTendpoint, String VRDRJson) throws ResourceAccessException, RestClientException{
		System.out.println("Submitting to nightingale at endpoint:" + POSTendpoint);
		ResponseEntity<String> POSTresponse
		  = restTemplate.postForEntity(POSTendpoint, VRDRJson, String.class);
		return POSTresponse;
	}

}