package edu.gatech.chai.Mapping.Util;

import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

import edu.gatech.chai.VRDR.model.DeathCertificateDocument;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;

public class FHIRCMSToVRDRUtil {
	private static final Coding caseNumberCodingType = new Coding("urn:mdi:temporary:code","1000007","Case Number");
	// Any 2 codes that share 1 coding is deemed equal
	public static boolean codeableConceptsEqual(CodeableConcept c1, CodeableConcept c2) {
		for(Coding coding1:c1.getCoding()) {
			for(Coding coding2:c2.getCoding()) {
				if(codingEquals(coding1, coding2)) {
					return true;
				}
			}
		}
		return false;
	}
	
	// Codings that have the same system and code are deemed equal
	public static boolean codingEquals(Coding c1, Coding c2) {
		if(c1.getCode().equals(c2.getCode()) && c1.getSystem().equals(c2.getSystem())) {
			return true;
		}
		return false;
	}
	
	public static Identifier findLocalPatientIdentifierInDCD(DeathCertificateDocument dcd) {
		Patient patient = findPatientinDCD(dcd);
		if(patient == null) {
			return null;
		}
		for(Identifier identifier:patient.getIdentifier()) {
			Coding curCoding = identifier.getType().getCodingFirstRep();
			if(codingEquals(curCoding,caseNumberCodingType)) {
				return identifier;
			}
		}
		return null;
	}
	
	public static Patient findPatientinDCD(DeathCertificateDocument dcd) {
		for(BundleEntryComponent bec:dcd.getEntry()) {
			Resource resource = bec.getResource();
			if(resource != null && resource.getResourceType().equals(ResourceType.Patient)) {
				Patient patient = (Patient)resource;
				return patient;
			}
		}
		return null;
	}
}
