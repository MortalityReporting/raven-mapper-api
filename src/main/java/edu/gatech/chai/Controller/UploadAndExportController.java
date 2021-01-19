package edu.gatech.chai.Controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.UnknownHostException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

import edu.gatech.chai.VRDR.model.DeathCertificateDocument;
import edu.gatech.chai.MDI.Model.MDIModelFields;
import edu.gatech.chai.Mapping.Service.CanaryValidationService;
import edu.gatech.chai.Mapping.Service.FhirCMSToVRDRService;
import edu.gatech.chai.Mapping.Service.MDIToFhirCMSService;
import edu.gatech.chai.Mapping.Service.NightingaleSubmissionService;
import edu.gatech.chai.Mapping.Service.VitalcheckSubmissionService;
import edu.gatech.chai.Submission.Configuration.SubmissionSourcesConfiguration;
import edu.gatech.chai.Submission.Entity.PatientSubmit;
import edu.gatech.chai.Submission.Entity.SourceStatus;
import edu.gatech.chai.Submission.Entity.Status;
import edu.gatech.chai.Submission.Repository.PatientSubmitRepository;
import edu.gatech.chai.Submission.Service.SubmitBundleService;

@Controller
public class UploadAndExportController {
	@Autowired
	MDIToFhirCMSService mappingService;
	@Autowired
	SubmitBundleService submitBundleService;
	@Autowired
	FhirCMSToVRDRService fhirCMSToVRDRService;
	@Autowired
	private CanaryValidationService canaryValidationService;
	@Autowired
	private NightingaleSubmissionService nightingaleSubmissionService;
	@Autowired
	private VitalcheckSubmissionService vitalcheckSubmissionService;
	@Autowired
	private SubmissionSourcesConfiguration submissionSourcesConfiguration;
	@Autowired
	private PatientSubmitRepository patientSubmitRepository;
	
	public UploadAndExportController() {
	}
	
    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("upload-csv-file")
    public String uploadCSVFile(@RequestParam("file") MultipartFile file, @Value(" ${fhircms.submit}") boolean submitFlag, Model model) {

        // validate file
        if (file.isEmpty()) {
            model.addAttribute("message", "Please select a CSV file to upload.");
            model.addAttribute("status", false);
        } else {

            // parse CSV file to create a list of `InputField` objects
            try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {

                // create csv bean reader
                CsvToBean<MDIModelFields> csvToBean = new CsvToBeanBuilder(reader)
                        .withType(MDIModelFields.class)
                        .withIgnoreLeadingWhiteSpace(true)
                        .build();

                // convert `CsvToBean` object to list of users
                List<MDIModelFields> inputFields = csvToBean.parse();
                String prettyFhirOutput = "";
                for(MDIModelFields inputField: inputFields) {
                	String jsonBundle = mappingService.convertToVRDRString(inputField);
                	System.out.println("JSON BUNDLE:");
                	System.out.println(jsonBundle);
                	ObjectMapper mapper = new ObjectMapper();
                    if(prettyFhirOutput.isEmpty()) {
                    	JsonNode node = mapper.readTree(jsonBundle);
                    	prettyFhirOutput = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
                    }
                    // Submit to fhir server
                    inputField.setSuccess(false);
                    System.out.println(jsonBundle);
                    if(submitFlag) {
	                    try {
	    	                ResponseEntity<String> response = submitBundleService.submitBundle(jsonBundle);
	    	                // save users list on model
	    	                if(response.getStatusCode() == HttpStatus.OK ) {
	    	                	inputField.setSuccess(true);
	    	                }
	                    }
	                    catch (HttpStatusCodeException e) {
	                    	inputField.setSuccess(false);
	                    }
                    }
                }
                model.addAttribute("inputFields", inputFields);
                model.addAttribute("status", true);
                model.addAttribute("fhirOutput", prettyFhirOutput);

            } catch (Exception ex) {
            	ex.printStackTrace(System.out);
                model.addAttribute("message", "An error occurred while processing the CSV file.");
                model.addAttribute("status", false);
            }
        }
        return "file-upload-status";
    }
    
    @GetMapping("submitEDRS")
    public ResponseEntity<JsonNode> submitEDRSRecord(@RequestParam(required = false) String patientIdentifier, @RequestParam(required = false) String systemIdentifier,
    		@RequestParam(required = false) String codeIdentifier, @RequestParam(defaultValue = "false") boolean validateOnly, @RequestParam(defaultValue = "false") boolean createOnly,
    		@RequestParam(defaultValue = "false") boolean submitOnly, @RequestParam(required = true) String endpointURL, @RequestParam(required = true, defaultValue = "nightingale") String endpointMode) {
    	ObjectMapper mapper = new ObjectMapper();
    	SourceStatus returnSource = new SourceStatus(endpointURL);
    	try {
			DeathCertificateDocument dcd = fhirCMSToVRDRService.createDCDFromBaseFhirServer(systemIdentifier, codeIdentifier);
			String VRDRJson = fhirCMSToVRDRService.getJsonParser().encodeResourceToString(dcd);
			String XMLJson = fhirCMSToVRDRService.getXmlParser().encodeResourceToString(dcd);
			System.out.println("VRDR Submission Document:");
			System.out.println(VRDRJson);
			System.out.println(XMLJson);
			//Create Only workflow
			if(createOnly) {
				JsonNode VRDROnly = mapper.readTree(VRDRJson);
				return new ResponseEntity<JsonNode>(VRDROnly, HttpStatus.OK);
			}
			/*//Validate Only workflow
			if(validateOnly) {
				JsonNode canaryIssues = canaryValidationService.validateVRDRAgainstCanary(VRDRJson);
				returnNode.set("validationIssues",canaryIssues);
				return new ResponseEntity<JsonNode>(returnNode, HttpStatus.OK);
			}*/
			//Nightingale submission
			if(endpointMode.equalsIgnoreCase("nightingale")) {
				returnSource = handleNightingaleSubmission(endpointURL, dcd, returnSource);
				
			}
			//Axiell submission
			else if(endpointMode.equalsIgnoreCase("axiell")) {
			}
			//Vitalcheck submission
			else if(endpointMode.equalsIgnoreCase("Vitalcheck")) {
				returnSource = handleVitalCheckSubmission(endpointURL, dcd, returnSource);
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
            ObjectNode exceptionNode = JsonNodeFactory.instance.objectNode();
            exceptionNode.put("Exception", ex.getMessage());
            ResponseEntity<JsonNode> response = new ResponseEntity<JsonNode>(exceptionNode, HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
		}
    	returnSource.setId(1);
    	JsonNode returnNode = mapper.valueToTree(returnSource);
    	ResponseEntity<JsonNode> response = new ResponseEntity<JsonNode>(returnNode, HttpStatus.OK);
    	return response;
    }
    
    //Check the database for an entity using the repository. If exists, grab it
    //For each source from configuration, check to see the status. If not sent, then make a new source and send
    
    @GetMapping("submitEDRS2.0")
    public ResponseEntity<JsonNode> submitEDRSRecord20(@RequestParam(required = true) String systemIdentifier,@RequestParam(required = true) String codeIdentifier) throws IOException {
    	ObjectMapper mapper = new ObjectMapper();
//    	if(systemIdentifier == null || codeIdentifier == null) {
//    		Iterable<PatientSubmit> repoEntity = patientSubmitRepository.findAll();
//    		JsonNode jsonOutput = mapper.valueToTree(repoEntity);
//    		HttpHeaders responseHeaders = new HttpHeaders();
//    	    responseHeaders.set("Content-Type", "application/json");
//    		ResponseEntity<JsonNode> returnResponse = new ResponseEntity<JsonNode>(jsonOutput, responseHeaders, HttpStatus.OK);
//    		return returnResponse;
//    	}
    	//Create Death Certificate
    	DeathCertificateDocument dcd = null;
		try {
			dcd = (DeathCertificateDocument) fhirCMSToVRDRService.pullDCDFromBaseFhirServer(systemIdentifier, codeIdentifier);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return new ResponseEntity<JsonNode>((JsonNode)mapper.valueToTree(e1.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
		}
    	String VRDRJson = fhirCMSToVRDRService.getJsonParser().encodeResourceToString(dcd);
		String XMLJson = fhirCMSToVRDRService.getXmlParser().encodeResourceToString(dcd);
		System.out.println("VRDR Submission Document:");
		System.out.println(VRDRJson);
		System.out.println(XMLJson);
    	//Check the database for an entity using the repository. If exists, grab it
    	//If not, create it.
		List<PatientSubmit> patientSubmitList = patientSubmitRepository.findByPatientIdentifierSystemAndPatientIdentifierCode(systemIdentifier, codeIdentifier);
		PatientSubmit submitEntity = null;
		if(patientSubmitList.isEmpty()) {
			submitEntity = new PatientSubmit(systemIdentifier, codeIdentifier);
		}
		else {
			submitEntity = patientSubmitList.get(0);
		}
		//For each source from configuration, check to see the status.
		//If not sent, then make a new source and send
		//Send based on mode: nightingale, or vitalcheck.
		for(int i=0; i < submissionSourcesConfiguration.getSourceurl().size(); i++) {
			String sourceurl = submissionSourcesConfiguration.getSourceurl().get(i);
			String sourcemode = submissionSourcesConfiguration.getSourcemode().get(i);
			SourceStatus source = null;
			try {
				source = submitEntity.getSources().stream()
					.filter(s -> s.getUrl().compareTo(sourceurl) == 0).findFirst().get();
			}
			catch(NoSuchElementException e) {
				source = new SourceStatus(sourceurl);
				submitEntity.getSources().add(source);
			}
			if(source.getStatus() == Status.completed) {
				continue;
			}
			try {
				if(sourcemode.equalsIgnoreCase("nightingale")) {
					source = handleNightingaleSubmission(sourceurl, dcd, source);
				}
				else if(sourcemode.equalsIgnoreCase("vitalcheck")) {
					source = handleVitalCheckSubmission(sourceurl, dcd, source);
				}
			}
			catch(RestClientException | IOException e) {
				source.addError("Request Error:" + e.getClass().getName() + ", " + e.getMessage());
			}
		}
		patientSubmitRepository.save(submitEntity);
		return submissionStatus(systemIdentifier,codeIdentifier);
    }
    
    @GetMapping("submitstatus")
    public ResponseEntity<JsonNode> submissionStatus(@RequestParam(required = true) String systemIdentifier,@RequestParam(required = true) String codeIdentifier){
    	ObjectMapper mapper = new ObjectMapper();
    	List<PatientSubmit> patientSubmitList = patientSubmitRepository.findByPatientIdentifierSystemAndPatientIdentifierCode(systemIdentifier, codeIdentifier);
    	JsonNode jsonOutput = mapper.valueToTree(patientSubmitList);
		HttpHeaders responseHeaders = new HttpHeaders();
	    responseHeaders.set("Content-Type", "application/json");
		ResponseEntity<JsonNode> returnResponse = new ResponseEntity<JsonNode>(jsonOutput, HttpStatus.OK);
		return returnResponse;
    }
    
    private SourceStatus handleNightingaleSubmission(String sourceurl, DeathCertificateDocument dcd, SourceStatus source) throws RestClientException, IOException {
    	if(source == null) {
    		source = new SourceStatus(sourceurl);
    	}
    	String VRDRJson = fhirCMSToVRDRService.getJsonParser().encodeResourceToString(dcd);
    	ResponseEntity<String> responseEntity = nightingaleSubmissionService.submitRecord(sourceurl, VRDRJson);
		source.setResponseCode(Integer.toString(responseEntity.getStatusCodeValue()));
		ObjectMapper mapper = new ObjectMapper();
		JsonNode nightingaleResponse = mapper.readTree(responseEntity.getBody());
		//Sometimes real response is embedded in a key name "PostEDRSResult"
		if(responseEntity.getStatusCode().is2xxSuccessful()) {
			if(nightingaleResponse.has("PostEDRSResult")) {
				nightingaleResponse = mapper.readTree(nightingaleResponse.get("PostEDRSResult").asText());
			}
			Pattern pattern = Pattern.compile("CreatedID:(\\d+)");
			String messageString = nightingaleResponse.get("message").asText().replaceAll(" ", "");
			Matcher matcher = pattern.matcher(messageString);
			matcher.matches();
			String nightingaleId = matcher.group(1);
			source.setIdentifier(nightingaleId);
			//Update the decedent and composition back in the original system as well
			fhirCMSToVRDRService.updateDecedentAndCompositionInCMS(sourceurl, nightingaleId, dcd);
			source.setStatus(Status.completed);
		}
		else {
			source.getError().add(nightingaleResponse.get("message").asText());
			source.setStatus(Status.error);
		}
		return source;
    }
    
    private SourceStatus handleVitalCheckSubmission(String sourceurl, DeathCertificateDocument dcd, SourceStatus source) throws RestClientException, IOException {
    	if(source == null) {
    		source = new SourceStatus(sourceurl);
    	}
    	ResponseEntity<String> responseEntity = vitalcheckSubmissionService.submitRecord(sourceurl, dcd);
		source.setResponseCode(Integer.toString(responseEntity.getStatusCodeValue()));
		source.setStatus(Status.completed);
		Bundle vitalCheckResponseBundle = (Bundle) fhirCMSToVRDRService.getJsonParser().parseResource(responseEntity.getBody());
		for(BundleEntryComponent bec:vitalCheckResponseBundle.getEntry()) {
			Resource resource = bec.getResource();
			//If we find a message header, read the identifier out of the system
			if(resource.getResourceType().equals(ResourceType.MessageHeader)) {
				//Expecting the response to have an extension with the value of the id in it
				MessageHeader messageHeader = (MessageHeader) resource;
				Extension extension = messageHeader.getResponse().getExtensionFirstRep();
				IntegerType integer = (IntegerType) extension.getValue();
				String vitalcheckId = integer.asStringValue();
				source.setIdentifier(vitalcheckId);
				fhirCMSToVRDRService.updateDecedentAndCompositionInCMS(sourceurl, vitalcheckId, dcd);
			}
			//If we find an OperOutcome, read all the errors
			if(resource.getResourceType().equals(ResourceType.OperationOutcome)) {
				OperationOutcome operationOutcome = (OperationOutcome) resource;
				for(Resource containedResource:operationOutcome.getContained()) {
					if(containedResource.getResourceType().equals(ResourceType.DetectedIssue)){
						DetectedIssue detectedIssue = (DetectedIssue)containedResource;
						source.addError(detectedIssue.getCode().getText());
					}
				}
			}
		}
		return source;
    }
}