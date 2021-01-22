package edu.gatech.chai.Mapping.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.CompositionAttestationMode;
import org.hl7.fhir.r4.model.Composition.CompositionAttesterComponent;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import edu.gatech.chai.VRDR.context.VRDRFhirContext;
import edu.gatech.chai.VRDR.model.AutopsyPerformedIndicator;
import edu.gatech.chai.VRDR.model.BirthRecordIdentifier;
import edu.gatech.chai.VRDR.model.CauseOfDeathCondition;
import edu.gatech.chai.VRDR.model.CauseOfDeathPathway;
import edu.gatech.chai.VRDR.model.Certifier;
import edu.gatech.chai.VRDR.model.ConditionContributingToDeath;
import edu.gatech.chai.VRDR.model.DeathCertificate;
import edu.gatech.chai.VRDR.model.DeathCertificateDocument;
import edu.gatech.chai.VRDR.model.DeathCertificateReference;
import edu.gatech.chai.VRDR.model.DeathCertification;
import edu.gatech.chai.VRDR.model.DeathDate;
import edu.gatech.chai.VRDR.model.DeathLocation;
import edu.gatech.chai.VRDR.model.DeathPronouncementPerformer;
import edu.gatech.chai.VRDR.model.Decedent;
import edu.gatech.chai.VRDR.model.DecedentAge;
import edu.gatech.chai.VRDR.model.DecedentDispositionMethod;
import edu.gatech.chai.VRDR.model.DecedentEducationLevel;
import edu.gatech.chai.VRDR.model.DecedentFather;
import edu.gatech.chai.VRDR.model.DecedentMilitaryService;
import edu.gatech.chai.VRDR.model.DecedentMother;
import edu.gatech.chai.VRDR.model.DecedentPregnancy;
import edu.gatech.chai.VRDR.model.DecedentSpouse;
import edu.gatech.chai.VRDR.model.DecedentTransportationRole;
import edu.gatech.chai.VRDR.model.DecedentUsualWork;
import edu.gatech.chai.VRDR.model.DispositionLocation;
import edu.gatech.chai.VRDR.model.ExaminerContacted;
import edu.gatech.chai.VRDR.model.InjuryIncident;
import edu.gatech.chai.VRDR.model.InjuryLocation;
import edu.gatech.chai.VRDR.model.MannerOfDeath;
import edu.gatech.chai.VRDR.model.Mortician;
import edu.gatech.chai.VRDR.model.TobaccoUseContributedToDeath;
import edu.gatech.chai.VRDR.model.util.AutopsyPerformedIndicatorUtil;
import edu.gatech.chai.VRDR.model.util.BirthRecordIdentifierUtil;
import edu.gatech.chai.VRDR.model.util.CommonUtil;
import edu.gatech.chai.VRDR.model.util.DeathCertificateUtil;
import edu.gatech.chai.VRDR.model.util.DeathCertificationUtil;
import edu.gatech.chai.VRDR.model.util.DeathDateUtil;
import edu.gatech.chai.VRDR.model.util.DecedentAgeUtil;
import edu.gatech.chai.VRDR.model.util.DecedentDispositionMethodUtil;
import edu.gatech.chai.VRDR.model.util.DecedentEducationLevelUtil;
import edu.gatech.chai.VRDR.model.util.DecedentFatherUtil;
import edu.gatech.chai.VRDR.model.util.DecedentMilitaryServiceUtil;
import edu.gatech.chai.VRDR.model.util.DecedentMotherUtil;
import edu.gatech.chai.VRDR.model.util.DecedentPregnancyUtil;
import edu.gatech.chai.VRDR.model.util.DecedentSpouseUtil;
import edu.gatech.chai.VRDR.model.util.DecedentTransportationRoleUtil;
import edu.gatech.chai.VRDR.model.util.DecedentUsualWorkUtil;
import edu.gatech.chai.VRDR.model.util.ExaminerContactedUtil;
import edu.gatech.chai.VRDR.model.util.InjuryIncidentUtil;
import edu.gatech.chai.VRDR.model.util.MannerOfDeathUtil;
import edu.gatech.chai.VRDR.model.util.TobaccoUseContributedToDeathUtil;
import edu.gatech.chai.Mapping.Util.FHIRCMSToVRDRUtil;

@Service
public class FhirCMSToVRDRService {

	@Autowired
	private VRDRFhirContext vrdrFhirContext;
	@Autowired
	private CanaryValidationService canaryValidationService;
	@Autowired
	private NightingaleSubmissionService nightingaleSubmissionService;
	private IGenericClient client;
	private IParser jsonParser;
	private IParser xmlParser;
	@Autowired
	public FhirCMSToVRDRService(VRDRFhirContext vrdrFhirContext, @Value("${fhircms.url}") String url,
	@Value("${fhircms.basicAuth.username}") String basicUsername,
	@Value("${fhircms.basicAuth.password}") String basicPassword) {
		this.vrdrFhirContext = vrdrFhirContext;
		client = vrdrFhirContext.getCtx().newRestfulGenericClient(url);
		IClientInterceptor authInterceptor = new BasicAuthInterceptor(basicUsername, basicPassword);
		client.registerInterceptor(authInterceptor);
		// Create a logging interceptor
		LoggingInterceptor loggingInterceptor = new LoggingInterceptor();

		// Optionally you may configure the interceptor (by default only
		// summary info is logged)
		loggingInterceptor.setLogRequestSummary(true);
		loggingInterceptor.setLogRequestBody(true);
		client.registerInterceptor(loggingInterceptor);
		jsonParser = vrdrFhirContext.getCtx().newJsonParser();
		xmlParser = vrdrFhirContext.getCtx().newXmlParser();
	}
	
	public void updateDecedentAndCompositionInCMS(String edrsSystemUrl, String edrsId, DeathCertificateDocument dcd) {
		for(BundleEntryComponent bec:dcd.getEntry()) {
			Resource resource = bec.getResource();
			if(resource.getResourceType().equals(ResourceType.Patient)) {
				Patient patient = (Patient)resource;
				Identifier newIdentifier = new Identifier().setSystem(edrsSystemUrl).setValue(edrsId);
				if(patient.getIdentifier().stream().anyMatch(identifier -> identifier.hasSystem() && identifier.getSystem().equals(newIdentifier.getSystem())
						&& identifier.hasValue() && identifier.getValue().equals(newIdentifier.getValue()))) {
					patient.addIdentifier(new Identifier().setSystem(edrsSystemUrl).setValue(edrsId));
					client.update().resource(patient).execute();
				}
			}
			if(resource.getResourceType().equals(ResourceType.Composition)) {
				Composition composition = (Composition)resource;
				client.update().resource(composition).execute();
			}
		}
	}
	
	public JsonNode pullDCDFromBaseFhirServerAsJson(String patientIdentifierSystem, String patientIdentifierCode) throws ResourceNotFoundException, IOException {
		DeathCertificateDocument DCD = pullDCDFromBaseFhirServer(patientIdentifierSystem, patientIdentifierCode);
		String rawString = jsonParser.encodeResourceToString(DCD);
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readTree(rawString);
	}
	
	public DeathCertificateDocument pullDCDFromBaseFhirServer(String patientIdentifierSystem, String patientIdentifierCode) throws ResourceNotFoundException {
		Bundle patientBundle = client.search()
				.forResource(Patient.class)
				.where(Patient.IDENTIFIER.exactly().systemAndCode(patientIdentifierSystem, patientIdentifierCode))
				.returnBundle(Bundle.class)
				.execute();
		Patient patient = null;
		if(patientBundle.getEntryFirstRep().getResource() != null) {
			patient = (Patient)patientBundle.getEntryFirstRep().getResource();
		}
		else {
			throw new ResourceNotFoundException("Patient not found in cms");
		}
		//Find the Death Certificate Composition in the system if it allready exists
		Bundle compositionBundle = client.search()
		.forResource(Composition.class)
		.where(Composition.PATIENT.hasId(patient.getIdElement().getIdPart()))
		.returnBundle(Bundle.class)
		.execute();
		if(!compositionBundle.hasEntry()) {
			throw new ResourceNotFoundException("Patient resource has no composition resources associated to them");
		}
		for(BundleEntryComponent bec:compositionBundle.getEntry()) {
			Composition currentComposition = (Composition)bec.getResource();
			if(FHIRCMSToVRDRUtil.codeableConceptsEqual(DeathCertificateUtil.typeFixedValue,currentComposition.getType())){
				Parameters outParams = client.operation()
						.onInstance(currentComposition.getIdElement())
						.named("document")
						.withNoParameters(Parameters.class)
						.preferResponseType(Bundle.class)
						.execute();
				ParametersParameterComponent ppc = outParams.getParameterFirstRep();
				if(ppc == null) {
					throw new ResourceNotFoundException("VRDR document not found in cms");
				}
				DeathCertificateDocument dcd = (DeathCertificateDocument)ppc.getResource();
				return dcd;
			}
		}
		throw new ResourceNotFoundException("Patient has compositions but no VRDR composition found");
	}
	
	public DeathCertificateDocument createDCDFromBaseFhirServer(String patientIdentifierSystem, String patientIdentifierCode) throws Exception {

		DeathCertificateDocument deathCertificateDocument = new DeathCertificateDocument();
		//Find the specific patient in the server with it's identifier
		Bundle patientBundle = client.search()
				.forResource(Patient.class)
				.where(Patient.IDENTIFIER.exactly().systemAndCode(patientIdentifierSystem, patientIdentifierCode))
				.returnBundle(Bundle.class)
				.execute();
		Patient patient = null;
		if(patientBundle.getEntryFirstRep().getResource() != null) {
			patient = (Patient)patientBundle.getEntryFirstRep().getResource();
		}
		else {
			throw new Exception("Patient not found in cms");
		}
		//Find the Death Certificate Composition in the system if it allready exists
		Bundle compositionBundle = client.search()
		.forResource(Composition.class)
		.where(Composition.PATIENT.hasId(patient.getIdElement().getIdPart()))
		.returnBundle(Bundle.class)
		.execute();
		DeathCertificate deathCertificate = new DeathCertificate();
		if(compositionBundle.hasEntry()) {
			Composition currentComposition = (Composition)compositionBundle.getEntryFirstRep().getResource();
			if(FHIRCMSToVRDRUtil.codeableConceptsEqual(DeathCertificateUtil.typeFixedValue,currentComposition.getType())){
				deathCertificate = (DeathCertificate)currentComposition;
			}
		}
		deathCertificateDocument.addEntry(new BundleEntryComponent().setResource(deathCertificate));
		//Find everything on the patient within the system
		Parameters patientEverythingOutParameters = client.operation()
				.onInstance(patient.getIdElement())
				.named("everything")
				.withNoParameters(Parameters.class)
				.useHttpGet()
				.execute();
		Bundle patientEverythingBundle = (Bundle)patientEverythingOutParameters.getParameterFirstRep().getResource();
		Certifier certifier = null;
		for(BundleEntryComponent bec: patientEverythingBundle.getEntry()) {
			Resource resource = bec.getResource();
			Meta meta = resource.getMeta();
			if(meta.isEmpty()) {
				meta.addProfile("notvalid://empty-profile");
				resource.setMeta(meta);
			}
			switch(resource.getResourceType()) {
				case CodeSystem:
					break;
				case Condition:
					addConditionToDeathCertificate((Condition)resource, deathCertificate, deathCertificateDocument);
					break;
				case DocumentReference:
					addDocumentReferenceToDeathCertificate((DocumentReference)resource, deathCertificate, deathCertificateDocument);
					break;
				case Encounter:
					break;
				case List:
					addListToDeathCertificate((ListResource)resource, deathCertificate, deathCertificateDocument);
					break;
				case Location:
					addLocationToDeathCertificate((Location)resource, deathCertificate, deathCertificateDocument);
					break;
				case Medication:
					break;
				case MedicationAdministration:
					break;
				case MedicationDispense:
					break;
				case MedicationRequest:
					break;
				case MedicationStatement:
					break;
				case Observation:
					addObservationToDeathCertificate((Observation)resource, deathCertificate, deathCertificateDocument, client);
					break;
				case Patient:
					addPatientToDeathCertificate((Patient)resource, deathCertificate, deathCertificateDocument);
					break;
				case Practitioner:
					addPractitionerToDeathCertificate((Practitioner)resource, deathCertificate, deathCertificateDocument);
					certifier = (Certifier)resource;
					break;
				case Procedure:
					addProcedureToDeathCertificate((Procedure)resource, deathCertificate, deathCertificateDocument);
					break;
				case RelatedPerson:
					addRelatedPersonToDeathCertificate((RelatedPerson)resource, deathCertificate, deathCertificateDocument);
					break;
				default:
					break;
			}
		}
		return deathCertificateDocument;
	}
	
	public void addConditionToDeathCertificate(Condition condition, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		if(condition instanceof CauseOfDeathCondition){
			CauseOfDeathCondition profiledCond = (CauseOfDeathCondition)condition;
			addReferenceToDeathCertificate(deathCertificate, profiledCond);
			addBundleEntry(dcd, profiledCond);
		}
		else if(condition instanceof ConditionContributingToDeath){
			ConditionContributingToDeath profiledCond = (ConditionContributingToDeath)condition;
			addReferenceToDeathCertificate(deathCertificate, profiledCond);
			addBundleEntry(dcd, profiledCond);
		}
	}
	
	public void addDocumentReferenceToDeathCertificate(DocumentReference documentReference, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		if(documentReference instanceof DeathCertificateReference){
			DeathCertificateReference profiledCond = (DeathCertificateReference)documentReference;
			addReferenceToDeathCertificate(deathCertificate, profiledCond);
			addBundleEntry(dcd, profiledCond);
		}
	}
	
	public void addListToDeathCertificate(ListResource listResource, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		addReferenceToDeathCertificate(deathCertificate, listResource);
		addBundleEntry(dcd, listResource);
	}
	
	public void addLocationToDeathCertificate(Location location, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		addReferenceToDeathCertificate(deathCertificate, location);
		addBundleEntry(dcd, location);
	}
	
	public void addObservationToDeathCertificate(Observation observation, DeathCertificate deathCertificate, DeathCertificateDocument dcd, IGenericClient client) {
		if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), AutopsyPerformedIndicatorUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), BirthRecordIdentifierUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DeathDateUtil.code)){
			DeathDate profiledObs = (DeathDate)observation;
			// Pull in referenced DeathLocation
			// Removed while extension location information doesn't seem to persist correctly into fhir server
			if(profiledObs.getPatientLocationExtension() != null) {
				Reference locationRef = (Reference)profiledObs.getPatientLocationExtension().getValue();
				try {
					Location location = client.read().resource(Location.class).withId(locationRef.getReference()).execute();
					addReferenceToDeathCertificate(deathCertificate, location );
					addBundleEntry(dcd, location);
				}
				catch(ResourceNotFoundException e) {
					System.out.println(e.getMessage());
				}
			}
			addReferenceToDeathCertificate(deathCertificate, profiledObs);
			addBundleEntry(dcd, profiledObs);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DecedentAgeUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DecedentDispositionMethodUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DecedentEducationLevelUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DecedentMilitaryServiceUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DecedentPregnancyUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DecedentTransportationRoleUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), DecedentUsualWorkUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), ExaminerContactedUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), InjuryIncidentUtil.code)){
			InjuryIncident profiledObs = (InjuryIncident)observation;
			addSectionEntry(deathCertificate, profiledObs);
			if(profiledObs.getPatientLocationExtension() != null) {
				Reference locationRef = (Reference)profiledObs.getPatientLocationExtension().getValue();
				try {
					Location location = client.read().resource(Location.class).withId(locationRef.getId()).execute();
					InjuryLocation injuryLocation = new InjuryLocation(location.getName(),location.getDescription(),
							location.getTypeFirstRep(),location.getAddress(),location.getPhysicalType());
					addSectionEntry(deathCertificate, injuryLocation);
					addBundleEntry(dcd, injuryLocation);
				}
				catch(NullPointerException e) {
					System.out.println(e.getMessage());
				}
			}
			addReferenceToDeathCertificate(deathCertificate, profiledObs);
			addBundleEntry(dcd, profiledObs);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), MannerOfDeathUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(observation.getCode(), TobaccoUseContributedToDeathUtil.code)){
			addReferenceToDeathCertificate(deathCertificate, observation);
			addBundleEntry(dcd, observation);
		}
	}
	
	public void addPatientToDeathCertificate(Patient patient, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		if(patient instanceof Decedent){
			Decedent profiledPatient = (Decedent)patient;
			addReferenceToDeathCertificate(deathCertificate, profiledPatient);
			addBundleEntry(dcd, profiledPatient);
		}
	}
	
	public void addPractitionerToDeathCertificate(Practitioner practitioner, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		if(practitioner instanceof Certifier){
			Certifier profiledPrac = (Certifier)practitioner;
			addReferenceToDeathCertificate(deathCertificate, profiledPrac);
			//Hack around to update the reference from "Prac/123" to "123"
			CompositionAttesterComponent oldcac = null;
			if(deathCertificate.hasAttester()) {
				oldcac = deathCertificate.getAttesterFirstRep();
				deathCertificate.setAttester(new ArrayList());
			}
			CompositionAttesterComponent newcac = new CompositionAttesterComponent();
			newcac.setMode(CompositionAttestationMode.LEGAL);
			if(oldcac != null && oldcac.getTime() != null) {
				newcac.setTime(oldcac.getTime());
			}
			else {
				newcac.setTime(new Date());
			}
			Reference certifierReference = new Reference(profiledPrac.getResourceType().toString() + "/" + profiledPrac.getIdElement().getIdPart());
			newcac.setParty(certifierReference);
			deathCertificate.setAttester(new ArrayList<CompositionAttesterComponent>());
			deathCertificate.addAttester(newcac);
			addBundleEntry(dcd, profiledPrac);
		}
		else if(practitioner instanceof DeathPronouncementPerformer){
			DeathPronouncementPerformer profiledPrac = (DeathPronouncementPerformer)practitioner;
			addReferenceToDeathCertificate(deathCertificate, profiledPrac);
			addBundleEntry(dcd, profiledPrac);
		}
		else if(practitioner instanceof Mortician){
			Mortician profiledPrac = (Mortician)practitioner;
			addReferenceToDeathCertificate(deathCertificate, profiledPrac);
			addBundleEntry(dcd, profiledPrac);
		}
	}
	
	public void addProcedureToDeathCertificate(Procedure procedure, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		addReferenceToDeathCertificate(deathCertificate, procedure);
		addBundleEntry(dcd, procedure);
	}
	
	public void addRelatedPersonToDeathCertificate(RelatedPerson relatedPerson, DeathCertificate deathCertificate, DeathCertificateDocument dcd) {
		if(FHIRCMSToVRDRUtil.codeableConceptsEqual(relatedPerson.getRelationshipFirstRep(), DecedentFatherUtil.code)){
			DecedentFather profiledRelatedPerson= (DecedentFather)relatedPerson;
			addReferenceToDeathCertificate(deathCertificate, profiledRelatedPerson);
			addBundleEntry(dcd, profiledRelatedPerson);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(relatedPerson.getRelationshipFirstRep(), DecedentMotherUtil.code)){
			DecedentMother profiledRelatedPerson= (DecedentMother)relatedPerson;
			addReferenceToDeathCertificate(deathCertificate, profiledRelatedPerson);
			addBundleEntry(dcd, profiledRelatedPerson);
		}
		else if(FHIRCMSToVRDRUtil.codeableConceptsEqual(relatedPerson.getRelationshipFirstRep(), DecedentSpouseUtil.code)){
			DecedentSpouse profiledRelatedPerson= (DecedentSpouse)relatedPerson;
			addReferenceToDeathCertificate(deathCertificate, profiledRelatedPerson);
			addBundleEntry(dcd, profiledRelatedPerson);
		}
	}
	
	private void addReferenceToDeathCertificate(DeathCertificate deathCertificate, Resource resource) {
		//Check if resource is allready in certificate
		boolean allreadyInCertificate = false;
		for(SectionComponent sc:deathCertificate.getSection()) {
			for(Reference reference:sc.getEntry()) {
				String referenceString = reference.getReference();
				String resourceIdString = resource.getId();
				if(referenceString.equals(resourceIdString)) {
					allreadyInCertificate = true;
				}
			}
		}
		if(!allreadyInCertificate) {
			addSectionEntry(deathCertificate, resource);
		}
	}
	
	private Decedent findDecedentInDCD(DeathCertificateDocument dcd) {
		for(BundleEntryComponent bec:dcd.getEntry()) {
			Resource resource = bec.getResource();
			if(resource instanceof Decedent) {
				Decedent decedent = (Decedent)resource;
				return decedent;
			}
		}
		return null;
	}
	
	private DeathCertificateDocument addBundleEntry(DeathCertificateDocument dcd,Resource resource) {
		dcd.addEntry().setResource(resource).setFullUrl(resource.getResourceType().toString() + "/" + resource.getIdElement().getIdPart());
		return dcd;
	}
	
	public static DeathCertificate addSectionEntry(DeathCertificate deathCertificate,Resource resource) {
		if(deathCertificate.getSection() == null || deathCertificate.getSection().isEmpty()) {
			deathCertificate.addSection(new SectionComponent());
		}
		SectionComponent sectionComponent = deathCertificate.getSectionFirstRep();
		for(Reference reference:sectionComponent.getEntry()) {
			if(reference.getReference().contains(resource.getIdElement().getIdPart())) {
				//Resource allready a section in the deathCertificate. Return
				return deathCertificate;
			}
		}
		resource.getId();
		sectionComponent.addEntry(new Reference(resource.getResourceType().toString() + "/" + resource.getIdElement().getIdPart()));
		return deathCertificate;
	}

	public IParser getJsonParser() {
		return jsonParser;
	}

	public void setJsonParser(IParser parser) {
		this.jsonParser = parser;
	}
	
	public IParser getXmlParser() {
		return xmlParser;
	}

	public void setXmlParser(IParser parser) {
		this.xmlParser = parser;
	}

	public VRDRFhirContext getVrdrFhirContext() {
		return vrdrFhirContext;
	}

	public void setVrdrFhirContext(VRDRFhirContext vrdrFhirContext) {
		this.vrdrFhirContext = vrdrFhirContext;
	}
}