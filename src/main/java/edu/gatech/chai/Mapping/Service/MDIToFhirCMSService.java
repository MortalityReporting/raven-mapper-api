package edu.gatech.chai.Mapping.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Address.AddressUse;
import org.hl7.fhir.r4.model.Age;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition.CompositionAttestationMode;
import org.hl7.fhir.r4.model.Composition.CompositionAttesterComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceContentComponent;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource.ListEntryComponent;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Procedure.ProcedureStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.parser.IParser;
import edu.gatech.chai.VRDR.context.VRDRFhirContext;
import edu.gatech.chai.VRDR.model.AutopsyPerformedIndicator;
import edu.gatech.chai.VRDR.model.CauseOfDeathCondition;
import edu.gatech.chai.VRDR.model.CauseOfDeathPathway;
import edu.gatech.chai.VRDR.model.Certifier;
import edu.gatech.chai.VRDR.model.ConditionContributingToDeath;
import edu.gatech.chai.VRDR.model.DeathCertificate;
import edu.gatech.chai.VRDR.model.DeathDate;
import edu.gatech.chai.VRDR.model.DeathLocation;
import edu.gatech.chai.VRDR.model.Decedent;
import edu.gatech.chai.VRDR.model.DecedentAge;
import edu.gatech.chai.VRDR.model.DecedentDispositionMethod;
import edu.gatech.chai.VRDR.model.DecedentUsualWork;
import edu.gatech.chai.VRDR.model.DispositionLocation;
import edu.gatech.chai.VRDR.model.ExaminerContacted;
import edu.gatech.chai.VRDR.model.InjuryIncident;
import edu.gatech.chai.VRDR.model.InjuryLocation;
import edu.gatech.chai.VRDR.model.MannerOfDeath;
import edu.gatech.chai.VRDR.model.util.CommonUtil;
import edu.gatech.chai.VRDR.model.util.InjuryIncidentUtil;
import edu.gatech.chai.MDI.Model.MDIModelFields;
import edu.gatech.chai.Mapping.Util.CommonMappingUtil;
import edu.gatech.chai.Mapping.Util.MDIToFhirCMSUtil;

@Service
public class MDIToFhirCMSService {
	
	@Autowired
	private VRDRFhirContext vrdrFhirContext;
	
	public String convertToVRDRString(MDIModelFields inputFields) throws ParseException {
		Bundle fullBundle = convertToVRDR(inputFields);
		IParser parser = vrdrFhirContext.getCtx().newJsonParser();
		String returnString = parser.encodeResourceToString(fullBundle);
		return returnString;
	}
	
	public Bundle convertToVRDR(MDIModelFields inputFields) throws ParseException {
		Bundle returnBundle = new Bundle();
		returnBundle.setType(BundleType.BATCH);
		// Handle Decedent
		Stream<String> decedentFields = Stream.of(inputFields.FIRSTNAME,inputFields.MIDNAME,inputFields.LASTNAME
				,inputFields.AGE,inputFields.AGEUNIT,inputFields.RACE,inputFields.GENDER
				,inputFields.ETHNICITY,inputFields.BIRTHDATE,inputFields.MRNNUMBER
				,inputFields.MARITAL,inputFields.POSSIBLEID,inputFields.RESSTREET
				,inputFields.RESCITY,inputFields.RESCOUNTY,inputFields.RESSTATE
				,inputFields.RESZIP,inputFields.RESNAME,inputFields.LKAWHERE,inputFields.HOSPNAME
				,inputFields.CASEID);
		Decedent decedentResource = null;
		Reference decedentReference = null;
		if(!decedentFields.allMatch(x -> x == null || x.isEmpty())) {
			decedentResource = createDecedent(inputFields);
			decedentReference = new Reference(decedentResource.getId());
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, decedentResource);
		}
		// Handle DecedentAge
		DecedentAge decedentAge = null;
		Stream<String> ageFields = Stream.of(inputFields.AGE,inputFields.AGEUNIT, inputFields.PRNDATE, inputFields.PRNTIME);
		if(!ageFields.allMatch(x -> x == null || x.isEmpty())) {
			decedentAge = createDecedentAge(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, decedentAge);
		}
		// Handle Employment History
		Stream<String> employmentHistoryFields = Stream.of(inputFields.JOBTITLE, inputFields.INDUSTRY);
		DecedentUsualWork decedentUsualWork = null;
		if(!employmentHistoryFields.allMatch(x -> x == null || x.isEmpty())) {
			decedentUsualWork = createDecedentEmploymentHistory(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, decedentUsualWork);
		}
		// Handle Certifier
		Stream<String> certifierFields = Stream.of(inputFields.CERTIFIER_NAME,inputFields.CERTIFIER_TYPE);
		Certifier certifierResource = null;
		if(!certifierFields.allMatch(x -> x == null || x.isEmpty())) {
			certifierResource = createCertifier(inputFields);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, certifierResource);
		}
		// Handle Cause Of Death Pathway
		Stream<String> causeOfDeathFields = Stream.of(inputFields.CAUSEA, inputFields.CAUSEB, inputFields.CAUSEC,
				inputFields.CAUSED,inputFields.OSCOND, inputFields.DURATIONA, inputFields.DURATIONB,
				inputFields.DURATIONC, inputFields.DURATIOND);
		if(!causeOfDeathFields.allMatch(x -> x == null || x.isEmpty())) {
			CauseOfDeathPathway causeOfDeathPathway = createCauseOfDeathPathway(inputFields, returnBundle, decedentReference, certifierResource);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, causeOfDeathPathway);
		}
		//  Handle Disposition Method
		if(inputFields.DISPMETHOD != null && !inputFields.DISPMETHOD.isEmpty()) {
			DecedentDispositionMethod dispositionMethod = createDispositionMethod(inputFields,decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, dispositionMethod);
		}
		//  Handle Disposition Location
		Stream<String> dispositionLocationFields = Stream.of(inputFields.DISP_STREET, inputFields.DISP_CITY, inputFields.DISP_COUNTY,
				inputFields.DISP_STATE,inputFields.DISP_ZIP);
		if(!dispositionLocationFields.allMatch(x -> x == null || x.isEmpty())) {
			DispositionLocation dispositionLocation = createDispositionLocation(inputFields,decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, dispositionLocation);
		}
		// Handle Injury Location
		Stream<String> injuryLocFields = Stream.of(inputFields.CINJSTREET,inputFields.CINJCITY,inputFields.CINJCOUNTY
				,inputFields.CINJSTATE,inputFields.CINJZIP);
		InjuryLocation injuryLocation = null;
		if(!injuryLocFields.allMatch(x -> x == null || x.isEmpty())) {
			injuryLocation = createInjuryLocation(inputFields);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, injuryLocation);
		}
		// Handle Injury Incident
		Stream<String> injuryIncidentFields = Stream.of(inputFields.CHOWNINJURY, inputFields.ATWORK,
				inputFields.JOBRELATED, inputFields.CINJDATE, inputFields.CINJTIME, inputFields.CIDATEFLAG,
				inputFields.CUSTODY);
		if(!injuryIncidentFields.allMatch(x -> x == null || x.isEmpty())) {
			InjuryIncident injuryIncident = createInjuryIncident(inputFields, decedentReference, injuryLocation);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, injuryIncident);
		}
		// Handle Manner Of Death
		Stream<String> mannerFields = Stream.of(inputFields.MANNER);
		if(!mannerFields.allMatch(x -> x == null || x.isEmpty())) {
			MannerOfDeath manner = createMannerOfDeath(inputFields, decedentReference, null);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, manner);
		}
		// Handle CaseNotes
		if(inputFields.CASENOTES != null && !inputFields.CASENOTES.isEmpty()) {
			for(String caseNote: inputFields.CASENOTES.split(";")) {
				DocumentReference caseNoteResource = createCaseNote(caseNote,decedentReference);
				MDIToFhirCMSUtil.addResourceToBundle(returnBundle, caseNoteResource);
			}
		}
		// Handle ExaminerContacted
		Stream<String> examinerContactedFields = Stream.of(inputFields.REPORTDATE, inputFields.REPORTTIME);
		if(!examinerContactedFields.allMatch(x -> x == null || x.isEmpty())) {
			ExaminerContacted examinerContacted = createExaminerContacted(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, examinerContacted);
		}
		// Handle Found Observation
		Stream<String> foundFields = Stream.of(inputFields.FOUNDDATE, inputFields.FOUNDTIME);
		if(!foundFields.allMatch(x -> x == null || x.isEmpty())) {
			Observation found = createFoundObs(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, found);
		}
		// Handle Death Location
		DeathLocation deathLocation = null;
		Stream<String> deathLocFields = Stream.of(inputFields.DEATHPLACE, inputFields.DEATHSTREET,
				inputFields.DEATHCITY, inputFields.DEATHCOUNTY, inputFields.DEATHSTATE, inputFields.DEATHZIP,
				inputFields.FOUNDADDR_STREET,inputFields.FOUNDADDR_CITY,inputFields.FOUNDADDR_COUNTY,
				inputFields.FOUNDADDR_STATE,inputFields.FOUNDADDR_ZIP,inputFields.PRNPLACE,inputFields.PRNSTREET,
				inputFields.PRNCITY,inputFields.PRNCOUNTY,inputFields.PRNSTATE,inputFields.PRNZIP,
				inputFields.SCENEADDR_STREET,inputFields.SCENEADDR_CITY,inputFields.SCENEADDR_COUNTY,
				inputFields.SCENEADDR_STATE,inputFields.SCENEADDR_ZIP);
		if(!deathLocFields.allMatch(x -> x == null || x.isEmpty())) {
			deathLocation = createDeathLocation(inputFields);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, deathLocation);
		}
		// Handle Death Date
		Stream<String> deathDateFields = Stream.of(inputFields.PRNDATE, inputFields.PRNTIME, inputFields.CDEATHFLAG,
				inputFields.CDEATHDATE, inputFields.CDEATHDATE, inputFields.CDEATHTIME);
		if(!deathDateFields.allMatch(x -> x == null || x.isEmpty())) {
			DeathDate deathDate = createDeathDate(inputFields, decedentReference, deathLocation);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, deathDate);
		}
		// Handle Date Examined Observation
		Stream<String> dateExaminedFields = Stream.of(inputFields.EXAMDATE);
		if(!dateExaminedFields.allMatch(x -> x == null || x.isEmpty())) {
			Observation dateExamined = createDateExaminedObs(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, dateExamined);
		}
		// Handle Last Known Alive Observation
		Stream<String> lkaFields = Stream.of(inputFields.LKADATE, inputFields.LKATIME);
		if(!lkaFields.allMatch(x -> x == null || x.isEmpty())) {
			Observation lastKnownAlive = createLKAObs(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, lastKnownAlive);
		}
		// Handle Case Year
		Stream<String> caseYearFields = Stream.of(inputFields.CASEYEAR);
		if(!caseYearFields.allMatch(x -> x == null || x.isEmpty())) {
			Observation caseYear = createCaseYearObs(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, caseYear);
		}
		// Handle Hospital DateTime
		Stream<String> hospDTFields = Stream.of(inputFields.ATHOSPDATE, inputFields.ATHOSPTIME);
		if(!hospDTFields.allMatch(x -> x == null || x.isEmpty())) {
			Observation caseYear = createHospitalDateTime(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, caseYear);
		}
		// Handle Surgery
		Stream<String> surgeryFields = Stream.of(inputFields.SURGERY,inputFields.SURGDATE, inputFields.SURGREASON);
		if(!surgeryFields.allMatch(x -> x == null || x.isEmpty())) {
			Procedure surgery = createSurgeryProc(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, surgery);
		}
		// Handle Autopsy
		Stream<String> autopsyFields = Stream.of(inputFields.CAUTOPSY,inputFields.AUTOPUSED);
		if(!autopsyFields.allMatch(x -> x == null || x.isEmpty())) {
			AutopsyPerformedIndicator autopsy = createAutopsy(inputFields, decedentReference);
			MDIToFhirCMSUtil.addResourceToBundle(returnBundle, autopsy);
		}
		//Create Death Certificate to house the certifier and decedent reference
		DeathCertificate deathCertificate = new DeathCertificate();
		if(certifierResource != null) {
			CompositionAttesterComponent cac = new CompositionAttesterComponent();
			cac.setMode(CompositionAttestationMode.LEGAL);
			cac.setTime(new Date());
			cac.setParty(new Reference(certifierResource.getId()));
			deathCertificate.addAttester(cac);
		}
		deathCertificate.setSubject(decedentReference);
		MDIToFhirCMSUtil.addResourceToBundle(returnBundle, deathCertificate);
		return returnBundle;
	}
	
	private Decedent createDecedent(MDIModelFields inputFields) throws ParseException {
		Decedent returnDecedent = new Decedent();
		Stream<String> caseIdFields = Stream.of(inputFields.SYSTEMID,inputFields.CASEID);
		if(!caseIdFields.allMatch(x -> x == null || x.isEmpty())) {
			Identifier identifier = new Identifier().setSystem(inputFields.SYSTEMID);
			identifier.setValue(inputFields.CASEID);
			identifier.setType(new CodeableConcept().addCoding(new Coding().setCode("1000007").setSystem("urn:mdi:temporary:code").setDisplay("Case Number")));
			returnDecedent.addIdentifier(identifier);
		}
		Stream<String> nameFields = Stream.of(inputFields.FIRSTNAME,inputFields.LASTNAME,inputFields.MIDNAME,inputFields.POSSIBLEID);
		if(!nameFields.allMatch(x -> x == null || x.isEmpty())) {
			HumanName name = new HumanName();
			name.addGiven(inputFields.FIRSTNAME);
			name.setFamily(inputFields.LASTNAME);
			name.addGiven(inputFields.MIDNAME);
			name.setUse(NameUse.OFFICIAL);
			returnDecedent.addName(name);
		}
		if(inputFields.RACE != null && !inputFields.RACE.isEmpty()) {
			if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.RACE, "White")) {
				returnDecedent.setRace("2106-3", "", inputFields.RACE);
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.RACE, "Hawaiian") || MDIToFhirCMSUtil.containsIgnoreCase(inputFields.RACE, "Pacific")) {
				returnDecedent.setRace("2076-8", "", inputFields.RACE);
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.RACE, "Asian")) {
				returnDecedent.setRace("2028-9", "", inputFields.RACE);
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.RACE, "Indian") || MDIToFhirCMSUtil.containsIgnoreCase(inputFields.RACE, "Native")) {
				returnDecedent.setRace("1002-5", "", inputFields.RACE);
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.RACE, "Black")) {
				returnDecedent.setRace("2054-5", "", inputFields.RACE);
			}
		}
		if(inputFields.ETHNICITY != null && !inputFields.ETHNICITY.isEmpty()) {
			if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.ETHNICITY, "Not Hispanic") || MDIToFhirCMSUtil.containsIgnoreCase(inputFields.ETHNICITY, "Malaysian")) {
				returnDecedent.setEthnicity("2186-5", "", inputFields.ETHNICITY);
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.ETHNICITY, "Hispanic") || MDIToFhirCMSUtil.containsIgnoreCase(inputFields.ETHNICITY, "Salvadoran") ||MDIToFhirCMSUtil.containsIgnoreCase(inputFields.ETHNICITY, "Latino")) {
				returnDecedent.setEthnicity("2135-2", "", inputFields.ETHNICITY);
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.ETHNICITY, "Cuban")) {
				returnDecedent.setEthnicity("2135-2", "2182-4", inputFields.ETHNICITY);
			}
		}
		if(inputFields.GENDER != null && !inputFields.GENDER.isEmpty()) {
			if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.GENDER, "Female")) {
				returnDecedent.setGender(AdministrativeGender.FEMALE);
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.GENDER, "Male")) {
				returnDecedent.setGender(AdministrativeGender.MALE);
			}
			else{
				returnDecedent.setGender(AdministrativeGender.UNKNOWN);
			}
		}
		if(inputFields.BIRTHDATE != null && !inputFields.BIRTHDATE.isEmpty()) {
			Date birthDate = MDIToFhirCMSUtil.parseDate(inputFields.BIRTHDATE);
			returnDecedent.setBirthDate(birthDate);
		}
		if(inputFields.MRNNUMBER != null && !inputFields.MRNNUMBER.isEmpty()) {
			Identifier identifier = new Identifier().setSystem("http://hl7.org/fhir/sid/us-ssn")
					.setValue(inputFields.MRNNUMBER);
			identifier.setType(new CodeableConcept().addCoding(new Coding().setCode("MR")
					.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203").setDisplay("Medical Record Number")));
			returnDecedent.addIdentifier(identifier);
		}
		if(inputFields.MARITAL != null && !inputFields.MARITAL.isEmpty()) {
			Coding martialCoding = new Coding();
			martialCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus");
			if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Annul")) {
				martialCoding.setCode("A");
				martialCoding.setDisplay("Annulled");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Divorce")) {
				martialCoding.setCode("D");
				martialCoding.setDisplay("Divorced");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Interlocutory")) {
				martialCoding.setCode("I");
				martialCoding.setDisplay("Interlocutory");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Polygamous")) {
				martialCoding.setCode("P");
				martialCoding.setDisplay("Polygamous");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Never Married") || MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "No")) {
				martialCoding.setCode("U");
				martialCoding.setDisplay("unmarried");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Domestic Partner")) {
				martialCoding.setCode("T");
				martialCoding.setDisplay("Domestic partner");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Widow")) {
				martialCoding.setCode("W");
				martialCoding.setDisplay("Widowed");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MARITAL, "Married")) {
				martialCoding.setCode("M");
				martialCoding.setDisplay("Married");
			}
			else {
				martialCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-NullFlavor");
				martialCoding.setCode("UNK");
				martialCoding.setDisplay("Unknown");
			}
		}
		Address residentAddress = MDIToFhirCMSUtil.createAddress(inputFields.RESSTREET,
				inputFields.RESCITY, inputFields.RESCOUNTY, inputFields.RESSTATE, inputFields.RESZIP);
		if(inputFields.RESNAME != null && !inputFields.RESNAME.isEmpty()) {
			Extension resNameExt = new Extension();
			resNameExt.setUrl("urn:oid:2.16.840.1.113883.11.20.9.49");
			Extension resNameTextExt = new Extension();
			resNameTextExt.setUrl("Text");
			resNameTextExt.setValue(new StringType(inputFields.RESNAME));
			resNameExt.addExtension(resNameTextExt);
			residentAddress.addExtension(resNameTextExt);
		}
		residentAddress.setUse(AddressUse.HOME);
		returnDecedent.addAddress(residentAddress);
		if(inputFields.LKAWHERE != null && !inputFields.LKAWHERE.isEmpty()) {
			Extension lkaExt = new Extension();
			lkaExt.setUrl("urn:mdi:temporary:code:last-known-to-be-alive-or-okay-place");
			lkaExt.setValue(new StringType(inputFields.LKAWHERE));
			returnDecedent.addExtension(lkaExt);
		}
		if(inputFields.HOSPNAME != null && !inputFields.HOSPNAME.isEmpty()) {
			Extension lkaExt = new Extension();
			lkaExt.setUrl("urn:mdi:temporary:code:hospital-name-decedent-was-first-taken");
			lkaExt.setValue(new StringType(inputFields.HOSPNAME));
			returnDecedent.addExtension(lkaExt);
		}
		if(inputFields.CASEID != null && !inputFields.CASEID.isEmpty()) {
			Identifier caseId = new Identifier();
			caseId.setSystem("urn:mdi:temporary:code");
			caseId.setValueElement(new StringType(inputFields.CASEID));
		}
		return returnDecedent;
	}
	
	private DecedentAge createDecedentAge(MDIModelFields inputFields, Reference decedentReference) {
		DecedentAge returnObservation = new DecedentAge();
		returnObservation.setSubject(decedentReference);
		Quantity ageQuantity = new Quantity();
		ageQuantity.setValue(new BigDecimal(inputFields.AGE)); //TODO: Check valid decimal
		ageQuantity.setUnit(inputFields.AGEUNIT); //TODO: Check to make sure it's in system
		ageQuantity.setSystem("http://unitsofmeasure.org");
		ageQuantity.setCode(MDIToFhirCMSUtil.convertUnitOfMeasureStringToCode(inputFields.AGEUNIT));
		returnObservation.setValue(ageQuantity);
		return returnObservation;
	}
	
	private DecedentUsualWork createDecedentEmploymentHistory(MDIModelFields inputFields, Reference decedentReference) {
		DecedentUsualWork returnEmploymentHistory = new DecedentUsualWork();
		returnEmploymentHistory.setSubject(decedentReference);
		if(inputFields.JOBTITLE != null && !inputFields.JOBTITLE.isEmpty()) {
			CodeableConcept usualOccupation = new CodeableConcept();
			usualOccupation.setText(inputFields.JOBTITLE);
			returnEmploymentHistory.setValue(usualOccupation);
		}
		if(inputFields.INDUSTRY != null && !inputFields.INDUSTRY.isEmpty()) {
			CodeableConcept usualIndustry = new CodeableConcept();
			usualIndustry.setText(inputFields.INDUSTRY);
			returnEmploymentHistory.addUsualIndustry(usualIndustry);
		}
		return returnEmploymentHistory;
	}
	
	private Certifier createCertifier(MDIModelFields inputFields) {
		Certifier returnCertifier = new Certifier();
		if(inputFields.CERTIFIER_NAME != null && !inputFields.CERTIFIER_NAME.isEmpty()) {
			HumanName certName = MDIToFhirCMSUtil.parseHumanName(inputFields.CERTIFIER_NAME);
			returnCertifier.addName(certName);
		}
		if(inputFields.CERTIFIER_TYPE != null && !inputFields.CERTIFIER_TYPE.isEmpty()) {
			//TODO: Add certifier qualification component that makes sense here
		}
		return returnCertifier;
	}
	
	private InjuryIncident createInjuryIncident(MDIModelFields inputFields, Reference decedentReference, Location injuryLocation) throws ParseException {
		InjuryIncident returnIncident = new InjuryIncident();
		returnIncident.setSubject(decedentReference);
		if(injuryLocation != null) {
			returnIncident.addPatientLocationExtension(injuryLocation);
		}
		if(inputFields.ATWORK !=  null && !inputFields.ATWORK.isEmpty()) {
			boolean atWork = CommonMappingUtil.parseBoolean(inputFields.ATWORK);
			returnIncident.addInjuredAtWorkComponent(CommonMappingUtil.parseBooleanAndCreateCode(inputFields.ATWORK));
			if(inputFields.JOBRELATED !=  null && !inputFields.JOBRELATED.isEmpty()) {
				//Search for the atWork component we just made
				for (ObservationComponentComponent component: returnIncident.getComponent()) {
					if(component.getCode().equalsShallow(InjuryIncidentUtil.componentInjuryAtWorkCode)) {
						Extension jobRelatedExtension = new Extension();
						jobRelatedExtension.setUrl("urn:mdi:temporary:code:constitute-osha-injury-at-work");
						BooleanType value = new BooleanType(CommonMappingUtil.parseBoolean(inputFields.JOBRELATED));
						jobRelatedExtension.setValue(value);
						component.addModifierExtension(jobRelatedExtension);
					}
				}
			}
		}
		if(inputFields.CINJDATE != null && !inputFields.CINJDATE.isEmpty()) {
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.CINJDATE);
			if(inputFields.CINJTIME != null && !inputFields.CINJTIME.isEmpty()) {
				MDIToFhirCMSUtil.addTimeToDate(reportDate, inputFields.CINJTIME);
			}
			returnIncident.setEffective(new DateTimeType(reportDate));
		}
		if(inputFields.CIDATEFLAG != null && !inputFields.CIDATEFLAG.isEmpty()) {
			Extension qualificationExtension = new Extension();
			qualificationExtension.setUrl("urn:mdi:temporary:code:qualification-of-injury-date");
			qualificationExtension.setValue(new StringType(inputFields.CIDATEFLAG));
			returnIncident.addExtension(qualificationExtension);
		}
		if(inputFields.CHOWNINJURY !=  null && !inputFields.CHOWNINJURY.isEmpty()) {
			returnIncident.setValue(new StringType(inputFields.CHOWNINJURY));
		}
		if(inputFields.CUSTODY !=  null && !inputFields.CUSTODY.isEmpty()) {
			ObservationComponentComponent custodyComp = new ObservationComponentComponent();
			custodyComp.setCode(new CodeableConcept().addCoding(new Coding("urn:mdi:temporary:code","100002","Death in custody")));
			custodyComp.setValue(new BooleanType(CommonMappingUtil.parseBoolean(inputFields.CUSTODY)));
			returnIncident.addComponent(custodyComp);
		}
		return returnIncident;
	}
	
	private CauseOfDeathPathway createCauseOfDeathPathway(MDIModelFields inputFields, Bundle bundle, Reference decedentReference, Certifier certifier) {
		CauseOfDeathPathway returnCoDPathway = new CauseOfDeathPathway();
		returnCoDPathway.setSubject(decedentReference);
		if (certifier != null) {
			Reference certifierReference = new Reference();
			certifierReference.setReference(certifier.getId());
			returnCoDPathway.setSource(certifierReference);
		}
		List<String> causes = new ArrayList<String>(Arrays.asList(inputFields.CAUSEA,inputFields.CAUSEB,inputFields.CAUSEC,inputFields.CAUSED));
		List<String> durations = Arrays.asList(inputFields.DURATIONA,inputFields.DURATIONB,inputFields.DURATIONC,inputFields.DURATIOND);
		for(int i = 0; i < causes.size(); i++) {
			String cause = causes.get(i);
			String duration = (i < durations.size()) ? durations.get(i) : "";
			if(cause != null && !cause.isEmpty()) {
				CauseOfDeathCondition causeOfDeathCondition = new CauseOfDeathCondition();
				if(decedentReference != null) {
					causeOfDeathCondition.setSubject(decedentReference);
				}
				if(certifier != null) {
					causeOfDeathCondition.setCertifier(certifier);
				}
				causeOfDeathCondition.setCode(new CodeableConcept().setText(cause));
				if(!duration.isEmpty()) {
					Age durationAge = MDIToFhirCMSUtil.parseAge(duration);
					if(durationAge != null) {
						causeOfDeathCondition.setOnset(durationAge);
					}
					else {
						causeOfDeathCondition.setOnset(new StringType(duration));
					}
				}
				MDIToFhirCMSUtil.addResourceToBundle(bundle, causeOfDeathCondition);
				returnCoDPathway.addEntry(new ListEntryComponent().setItem(new Reference(causeOfDeathCondition.getId())));
			}
		}
		String[] otherCauses = inputFields.OSCOND.split(";");
		List<String> listArrayOtherCauses = Arrays.asList(otherCauses);
		for(String otherCause:listArrayOtherCauses) {
			if(otherCause.isEmpty()) {
				continue;
			}
			ConditionContributingToDeath conditionContrib = new ConditionContributingToDeath();
			if(decedentReference != null) {
				conditionContrib.setSubject(decedentReference);
			}
			if(certifier != null) {
				conditionContrib.setCertifier(certifier);
			}
			conditionContrib.setCode(new CodeableConcept().setText(otherCause));
			MDIToFhirCMSUtil.addResourceToBundle(bundle, conditionContrib);
		}
		return returnCoDPathway;
	}
	
	private MannerOfDeath createMannerOfDeath(MDIModelFields inputFields, Reference decedentReference, Reference certifierReference) {
		MannerOfDeath manner = new MannerOfDeath();
		manner.addPerformer(certifierReference);
		Coding mannerCoding = new Coding();
		if(inputFields.MANNER != null && !inputFields.MANNER.isEmpty()) {
			mannerCoding.setSystem("http://snomed.info/sct");
			if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MANNER, "Homicide")) {
				mannerCoding.setCode("27935005");
				mannerCoding.setDisplay("Homicide");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MANNER, "Suicide")) {
				mannerCoding.setCode("44301001");
				mannerCoding.setDisplay("Suicide");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MANNER, "Accident")) {
				mannerCoding.setCode("7878000");
				mannerCoding.setDisplay("Accidental Death");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MANNER, "Natural")) {
				mannerCoding.setCode("38605008");
				mannerCoding.setDisplay("Natural");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.MANNER, "Investigation")) {
				mannerCoding.setCode("185973002");
				mannerCoding.setDisplay("Patient awaiting investigation");
			}
			else {
				mannerCoding.setCode("65037004");
				mannerCoding.setDisplay("Death, manner undetermined");
			}
		}
		manner.setValue(new CodeableConcept().addCoding(mannerCoding));
		manner.setSubject(decedentReference);
		return manner;
	}
	
	private DecedentDispositionMethod createDispositionMethod(MDIModelFields inputFields, Reference decedentReferece) {
		DecedentDispositionMethod returnDispMethod = new DecedentDispositionMethod();
		if(inputFields.DISPMETHOD != null && !inputFields.DISPMETHOD.isEmpty()) {
			returnDispMethod.setValue(null, inputFields.DISPMETHOD);
		}
		if(decedentReferece != null && !decedentReferece.isEmpty()) {
			returnDispMethod.setSubject(decedentReferece);
		}
		return returnDispMethod;
	}
	
	private DispositionLocation createDispositionLocation(MDIModelFields inputFields, Reference decedentReferece) {
		DispositionLocation returnDispLocation = new DispositionLocation();
		Address dispAddr = MDIToFhirCMSUtil.createAddress(inputFields.DISP_STREET, inputFields.DISP_CITY, inputFields.DISP_COUNTY, inputFields.DISP_STATE, inputFields.DISP_ZIP);
		returnDispLocation.setAddress(dispAddr);
		if(inputFields.DISPPLACE!= null && !inputFields.DISPPLACE.isEmpty()) {
			CodeableConcept physicalTypeCode = new CodeableConcept();
			Coding physicalTypeCoding = new Coding();
			physicalTypeCoding.setSystem("http://hl7.org/fhir/ValueSet/location-physical-type");
			if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "Site")) {
				physicalTypeCoding.setCode("si");
				physicalTypeCoding.setDisplay("Site");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "Building")) {
				physicalTypeCoding.setCode("bu");
				physicalTypeCoding.setDisplay("Building");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "wing")) {
				physicalTypeCoding.setCode("wi");
				physicalTypeCoding.setDisplay("Wing");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "ward")) {
				physicalTypeCoding.setCode("wa");
				physicalTypeCoding.setDisplay("Ward");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "level")) {
				physicalTypeCoding.setCode("lvl");
				physicalTypeCoding.setDisplay("Level");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "corridor")) {
				physicalTypeCoding.setCode("co");
				physicalTypeCoding.setDisplay("Corridor");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "room")) {
				physicalTypeCoding.setCode("ro");
				physicalTypeCoding.setDisplay("Room");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "bed")) {
				physicalTypeCoding.setCode("bd");
				physicalTypeCoding.setDisplay("Bed");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "vechicle")) {
				physicalTypeCoding.setCode("ve");
				physicalTypeCoding.setDisplay("Vechicle");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "house")) {
				physicalTypeCoding.setCode("ho");
				physicalTypeCoding.setDisplay("House");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "cabinet")) {
				physicalTypeCoding.setCode("ca");
				physicalTypeCoding.setDisplay("Cabinet");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "Road")) {
				physicalTypeCoding.setCode("rd");
				physicalTypeCoding.setDisplay("road");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DISPPLACE, "Jurisdiction")) {
				physicalTypeCoding.setCode("jdn");
				physicalTypeCoding.setDisplay("Jurisidiction");
			}
			else {
				physicalTypeCoding.setCode("area");
				physicalTypeCoding.setDisplay("area");
			}
			
			physicalTypeCode.addCoding(physicalTypeCoding);
			returnDispLocation.setPhysicalType(physicalTypeCode);
		}
		return returnDispLocation;
	}
	
	private DocumentReference createCaseNote(String caseNoteString, Reference decedentReference) {
		DocumentReference caseNote = new DocumentReference();
		caseNote.setType(new CodeableConcept().
				addCoding(new Coding("http://loinc.org","47046-8","Summary of death note")));
		caseNote.setSubject(decedentReference);
		caseNote.setDate(new Date());
		DocumentReferenceContentComponent contentComponent = new DocumentReferenceContentComponent();
		Attachment attachment = new Attachment();
		attachment.setContentType("text/plain");
		attachment.setLanguage("en-US");
		attachment.setData(caseNoteString.getBytes());
		contentComponent.setAttachment(attachment);
		caseNote.addContent(contentComponent);
		return caseNote;
	}
	
	private ExaminerContacted createExaminerContacted(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		ExaminerContacted examinerContacted = new ExaminerContacted(true);
		examinerContacted.setSubject(decedentReference);
		if(inputFields.REPORTDATE != null && !inputFields.REPORTDATE.isEmpty()) {
			ObservationComponentComponent component = new ObservationComponentComponent();
			component.setCode(new CodeableConcept().addCoding(new Coding(
					"urn:oid:2.16.840.1.113883.6.96", "399651003", "Report of Date")));
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.REPORTDATE);
			if(inputFields.REPORTTIME != null && !inputFields.REPORTTIME.isEmpty()) {
				MDIToFhirCMSUtil.addTimeToDate(reportDate, inputFields.REPORTTIME);
			}
			component.setValue(new DateTimeType(reportDate));
			examinerContacted.addComponent(component);
		}
		return examinerContacted;
	}
	
	private Observation createFoundObs(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		Observation returnObs = new Observation();
		returnObs.setStatus(ObservationStatus.FINAL);
		returnObs.setSubject(decedentReference);
		returnObs.setCode(new CodeableConcept().addCoding(new Coding(
				"urn:mdi:temporary:code", "1000001", "Date and Time found dead, unconcious and in distress")));
		if(inputFields.FOUNDDATE != null && !inputFields.FOUNDDATE.isEmpty()) {
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.FOUNDDATE);
			if(inputFields.FOUNDTIME != null && !inputFields.FOUNDTIME.isEmpty()) {
				MDIToFhirCMSUtil.addTimeToDate(reportDate, inputFields.FOUNDTIME);
			}
			returnObs.setValue(new DateTimeType(reportDate));
		}
		return returnObs;
	}
	
	private DeathDate createDeathDate(MDIModelFields inputFields, Reference decedentReference, Location location) throws ParseException {
		DeathDate returnDeathDate = new DeathDate();
		returnDeathDate.setSubject(decedentReference);
		if(location != null && !location.isEmpty()) {
			returnDeathDate.addPatientLocationExtension(location);
		}
		/*if(inputFields.CDEATHDATE != null && !inputFields.CDEATHDATE.isEmpty()) {
			Date certDate = MDIToFhirCMSUtil.parseDate(inputFields.CDEATHDATE);
			if(inputFields.CDEATHTIME != null && !inputFields.CDEATHTIME.isEmpty()) {
				MDIToFhirCMSUtil.addTimeToDate(certDate, inputFields.CDEATHTIME);
			}
			returnDeathDate.setEffective(new DateTimeType(certDate));
		}*/
		if(inputFields.PRNDATE != null && !inputFields.PRNDATE.isEmpty()) {
			Date prnDate = MDIToFhirCMSUtil.parseDate(inputFields.PRNDATE);
			if(inputFields.PRNTIME != null && !inputFields.PRNTIME.isEmpty()) {
				MDIToFhirCMSUtil.addTimeToDate(prnDate, inputFields.PRNTIME);
			}
			DateTimeType deathDT = new DateTimeType(prnDate);
			returnDeathDate.setEffective(deathDT);
			returnDeathDate.setValue(deathDT);
			returnDeathDate.addDatePronouncedDead(deathDT);
		}
		if(inputFields.CDEATHFLAG != null && !inputFields.CDEATHFLAG.isEmpty()) {
			Extension qualificationExtension = new Extension();
			qualificationExtension.setUrl("urn:mdi:temporary:code:qualification-of-death-date");
			qualificationExtension.setValue(new StringType(inputFields.CDEATHFLAG));
			returnDeathDate.addExtension(qualificationExtension);
		}
		if(inputFields.CDEATHDATE != null && !inputFields.CDEATHDATE.isEmpty()) {
			Extension isCertifiedExt = new Extension();
			isCertifiedExt.setUrl("urn:mdi:temporary:code:is-certified");
			isCertifiedExt.setValue(new BooleanType(CommonMappingUtil.parseBoolean(inputFields.CDEATHDATE)));
		}
		return returnDeathDate;
	}
	
	private Observation createDateExaminedObs(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		Observation returnObs = new Observation();
		returnObs.setStatus(ObservationStatus.FINAL);
		returnObs.setSubject(decedentReference);
		returnObs.setCode(new CodeableConcept().addCoding(new Coding(
				"urn:mdi:temporary:code", "1000003", "Date of examination or case review")));
		if(inputFields.EXAMDATE != null && !inputFields.EXAMDATE.isEmpty()) {
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.EXAMDATE);
			returnObs.setValue(new DateTimeType(reportDate));
		}
		return returnObs;
	}
	
	private Observation createLKAObs(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		Observation returnObs = new Observation();
		returnObs.setStatus(ObservationStatus.FINAL);
		returnObs.setSubject(decedentReference);
		returnObs.setCode(new CodeableConcept().addCoding(new Coding(
				"urn:mdi:temporary:code", "1000004", "Date and time of last known alive or alert")));
		if(inputFields.LKADATE != null && !inputFields.LKADATE.isEmpty()) {
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.LKADATE);
			if(inputFields.LKATIME != null && !inputFields.LKATIME.isEmpty()) {
				MDIToFhirCMSUtil.addTimeToDate(reportDate, inputFields.LKATIME);
			}
			returnObs.setValue(new DateTimeType(reportDate));
		}
		return returnObs;
	}
	
	private Observation createCaseYearObs(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		Observation returnObs = new Observation();
		Meta meta = returnObs.getMeta();
		meta.addProfile("notvalid://empty-profile");
		returnObs.setMeta(meta);
		returnObs.setStatus(ObservationStatus.FINAL);
		returnObs.setSubject(decedentReference);
		returnObs.setCode(new CodeableConcept().addCoding(new Coding(
				"urn:mdi:temporary:code", "1000005", "Year by which case is categorized")));
		if(inputFields.CASEYEAR != null && !inputFields.CASEYEAR.isEmpty()) {
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.CASEYEAR);
			returnObs.setValue(new DateTimeType(reportDate));
		}
		return returnObs;
	}
	
	private Observation createHospitalDateTime(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		Observation returnObs = new Observation();
		Meta meta = returnObs.getMeta();
		meta.addProfile("notvalid://empty-profile");
		returnObs.setMeta(meta);
		returnObs.setStatus(ObservationStatus.FINAL);
		returnObs.setSubject(decedentReference);
		returnObs.setCode(new CodeableConcept().addCoding(new Coding(
				"urn:mdi:temporary:code", "1000006", "Date and time decedent arrived at hospital")));
		if(inputFields.ATHOSPDATE != null && !inputFields.ATHOSPDATE.isEmpty()) {
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.CASEYEAR);
			if(inputFields.ATHOSPTIME != null && !inputFields.ATHOSPTIME.isEmpty()) {
				MDIToFhirCMSUtil.addTimeToDate(reportDate, inputFields.ATHOSPTIME);
			}
			returnObs.setValue(new DateType(reportDate));
		}
		return returnObs;
	}
	
	private DeathLocation createDeathLocation(MDIModelFields inputFields) {
		DeathLocation returnDeathLocation = new DeathLocation();
		Stream<String> deathAddrFields = Stream.of(inputFields.DEATHSTREET, inputFields.DEATHCITY,
				inputFields.DEATHCOUNTY, inputFields.DEATHSTATE, inputFields.DEATHZIP);
		if(!deathAddrFields.allMatch(x -> x == null || x.isEmpty())) {
			Address deathAddr = MDIToFhirCMSUtil.createAddress(inputFields.DEATHSTREET, inputFields.DEATHCITY,
					inputFields.DEATHCOUNTY, inputFields.DEATHSTATE, inputFields.DEATHZIP);
			returnDeathLocation.setAddress(deathAddr);
		}
		CodeableConcept typeCode = new CodeableConcept();
		Coding typeCoding = new Coding();
		typeCoding.setSystem("http://snomed.info/sct");
		if(inputFields.DEATHPLACE != null && !inputFields.DEATHPLACE.isEmpty()) {
			//Adding deathplace as name and description as a stopgap measure.
			returnDeathLocation.setName(inputFields.DEATHPLACE);
			returnDeathLocation.setDescription(inputFields.DEATHPLACE);
			if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DEATHPLACE, "arrival") || MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DEATHPLACE, "DOA")) {
				typeCoding.setCode("63238001");
				typeCoding.setDisplay("Dead on arrival at hospital");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DEATHPLACE, "home")) {
				typeCoding.setCode("440081000124100");
				typeCoding.setDisplay("Death in home");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DEATHPLACE, "hospice")) {
				typeCoding.setCode("440071000124103");
				typeCoding.setDisplay("Death in hospice");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DEATHPLACE, "hospital") || MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DEATHPLACE, "emergency")) {
				typeCoding.setCode("450391000124102");
				typeCoding.setDisplay("Death in hospital-based emergency department or outpatient department (event)");
			}
			else if(MDIToFhirCMSUtil.containsIgnoreCase(inputFields.DEATHPLACE, "nursing")) {
				typeCoding.setCode("450381000124100");
				typeCoding.setDisplay("Death in nursing home or long term care facility (event)");
			}
			else{
				typeCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-NullFlavor");
				typeCoding.setCode("OTH");
				typeCoding.setDisplay("other");
			}
		}
		else {
			typeCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-NullFlavor");
			typeCoding.setCode("UNK");
			typeCoding.setDisplay("unknown");
		}
		typeCode.addCoding(typeCoding);
		returnDeathLocation.addType(typeCode);
		Extension foundAddrExt = new Extension();
		foundAddrExt.setUrl("urn:mdi:temporary:code:address-where-found-dead-unconscious-or-in-distress");
		Address foundAddr = MDIToFhirCMSUtil.createAddress(inputFields.FOUNDADDR_STREET, inputFields.FOUNDADDR_CITY,
				inputFields.FOUNDADDR_COUNTY, inputFields.FOUNDADDR_STATE, inputFields.FOUNDADDR_ZIP);
		foundAddrExt.setValue(foundAddr);
		returnDeathLocation.addExtension(foundAddrExt);
		Extension prnExt = new Extension();
		prnExt.setUrl("urn:mdi:temporary:code:pronounced-death-place");
		Address pronouncedDeadAddr = MDIToFhirCMSUtil.createAddress(inputFields.PRNSTREET, inputFields.PRNCITY,
				inputFields.PRNCOUNTY, inputFields.PRNSTATE, inputFields.PRNZIP);
		prnExt.setValue(pronouncedDeadAddr);
		returnDeathLocation.addExtension(prnExt);
		Extension sceneExt = new Extension();
		sceneExt.setUrl("urn:mdi:temporary:code:scene-address");
		Address sceneAddr = MDIToFhirCMSUtil.createAddress(inputFields.SCENEADDR_STREET, inputFields.SCENEADDR_CITY,
				inputFields.SCENEADDR_COUNTY, inputFields.SCENEADDR_STATE, inputFields.SCENEADDR_ZIP);
		sceneExt.setValue(sceneAddr);
		returnDeathLocation.addExtension(sceneExt);
		return returnDeathLocation;
	}
	
	private InjuryLocation createInjuryLocation(MDIModelFields inputFields) {
		InjuryLocation returnInjuryLocation = new InjuryLocation();
		Address eventAddress = MDIToFhirCMSUtil.createAddress(inputFields.CINJSTREET, inputFields.CINJCITY, inputFields.CINJCOUNTY,
				inputFields.CINJSTATE, inputFields.CINJZIP);
		returnInjuryLocation.setAddress(eventAddress);
		return returnInjuryLocation;
	}
	
	private Procedure createSurgeryProc(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		Procedure returnProcedure = new Procedure();
		Meta meta = returnProcedure.getMeta();
		meta.addProfile("notvalid://empty-profile");
		returnProcedure.setMeta(meta);
		returnProcedure.setSubject(decedentReference);
		returnProcedure.setCategory(new CodeableConcept().addCoding(new Coding(
				"http://snomed.info/sct","387713003","Surgical procedure")));
		CodeableConcept surgeryPerformedCode = null;
		if(inputFields.SURGERY != null && !inputFields.SURGERY.isEmpty()) {
			surgeryPerformedCode = CommonMappingUtil.parseBooleanAndCreateCode(inputFields.SURGERY);
		}
		else {
			surgeryPerformedCode = CommonUtil.noCode;
		}
		if(surgeryPerformedCode.equals(CommonUtil.yesCode)) {
			returnProcedure.setStatus(ProcedureStatus.COMPLETED);
		}
		else if(surgeryPerformedCode.equals(CommonUtil.unknownCode)) {
			returnProcedure.setStatus(ProcedureStatus.UNKNOWN);
		}
		else {
			returnProcedure.setStatus(ProcedureStatus.NOTDONE);
		}
		if(inputFields.SURGDATE != null && !inputFields.SURGDATE.isEmpty()) {
			Date reportDate = MDIToFhirCMSUtil.parseDate(inputFields.SURGDATE);
			returnProcedure.setPerformed(new DateTimeType(reportDate));
		}
		if(inputFields.SURGREASON != null && !inputFields.SURGREASON.isEmpty()) {
			CodeableConcept reasonCode = new CodeableConcept();
			reasonCode.setText(inputFields.SURGREASON);
			returnProcedure.addReasonCode(reasonCode);
		}
		return returnProcedure;
	}
	
	private AutopsyPerformedIndicator createAutopsy(MDIModelFields inputFields, Reference decedentReference) throws ParseException {
		AutopsyPerformedIndicator autopsy = new AutopsyPerformedIndicator();
		autopsy.setSubject(decedentReference);
		if(inputFields.CAUTOPSY != null && !inputFields.CAUTOPSY.isEmpty()) {
			autopsy.setValue(CommonMappingUtil.parseBooleanAndCreateCode(inputFields.CAUTOPSY));
		}
		if(inputFields.AUTOPUSED != null && !inputFields.AUTOPUSED.isEmpty()) {
			Extension autopsyUsedExt = new Extension();
			autopsyUsedExt.setUrl("urn:mdi:temporary:code:autopsy-findings-were-used");
			autopsyUsedExt.setValue(new BooleanType(CommonMappingUtil.parseBoolean(inputFields.AUTOPUSED)));
			autopsy.addExtension(autopsyUsedExt);
		}
		return autopsy;
	}
}