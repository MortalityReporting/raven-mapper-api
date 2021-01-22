package edu.gatech.chai.Mapping.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Age;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;

import edu.gatech.chai.VRDR.model.util.CommonUtil;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

public class MDIToFhirCMSUtil {
	public static List<String> dateFormatStrings = Arrays.asList("MM/dd/yyyy",
			"MM/dd/yy","dd-M-yyyy hh:mm:ss","dd MMMM yyyy","dd MMMM yyyy zzzz",
			"E, dd MMM yyyy HH:mm:ss z","MM-dd-yy","MM-dd-yyyy", "MMMM DD, YYYY", "MMdyyyy", "MMddyyyy", "MMddyy", "YYYY");
	public static List<String> timeFormatStrings = Arrays.asList("hh:mm:ss a", "hh:mm a",
			"hh:mm:ss", "hh:mm","hhmm","hhmmss");
	public static String ageRegex = "(\\d+)\\s*(year|month|week|day|hour|minute)";
	public static List<String> nameFormatStrings = Arrays.asList("(.*),\\s{0,1}(.*)\\s(.*)", "(\\w+)\\s(\\w+)");
	public static String convertUnitOfMeasureStringToCode(String uomString) {
		switch(uomString) {
			case "minutes":
				return "min";
			case "hours":
				return "h";
			case "days":
				return "d";
			case "weeks":
				return "wk";
			case "months":
				return "mo";
			default:
				return "a";
		}
	}
	public static Date parseDate(String dateString) throws ParseException {
		for (String formatString : dateFormatStrings)
	    {
	        try
	        {
	            return new SimpleDateFormat(formatString).parse(dateString);
	        }
	        catch (ParseException e) {}
	    }
		throw new ParseException("Could not format date: "+dateString, 0);
	}
	public static Date parseTime(String timeString) throws ParseException {
		for (String formatString : timeFormatStrings)
	    {
	        try
	        {
	            return new SimpleDateFormat(formatString).parse(timeString);
	        }
	        catch (ParseException e) {}
	    }
		throw new ParseException("Could not format time: "+timeString, 0);
	}
	public static Date addTimeToDate(Date date,String timeString) throws ParseException {
		Date timeDate = parseTime(timeString);
		date.setHours(timeDate.getHours());
		date.setMinutes(timeDate.getMinutes());
		date.setSeconds(timeDate.getSeconds());
		return date;
	}
	public static Date parseDateAndTime(String dateString, String timeString) throws ParseException {
		Date date = parseDate(dateString);
		date = addTimeToDate(date, timeString);
		return date;
	}
	public static boolean containsIgnoreCase(String src, String what) {
	    final int length = what.length();
	    if (length == 0)
	        return true; // Empty string is contained

	    final char firstLo = Character.toLowerCase(what.charAt(0));
	    final char firstUp = Character.toUpperCase(what.charAt(0));

	    for (int i = src.length() - length; i >= 0; i--) {
	        // Quick check before calling the more expensive regionMatches() method:
	        final char ch = src.charAt(i);
	        if (ch != firstLo && ch != firstUp)
	            continue;

	        if (src.regionMatches(true, i, what, 0, length))
	            return true;
	    }

	    return false;
	}
	
	
	
	public static Bundle addResourceToBundle(Bundle bundle,Resource resource) {
		if(resource.getId() == null || resource.getId().isEmpty()) {
			resource.setId(new IdType(UUID.randomUUID().toString()));
		}
		BundleEntryComponent bec = new BundleEntryComponent();
		bec.setFullUrl(resource.getId());
		bec.setRequest(new BundleEntryRequestComponent().setMethod(HTTPVerb.POST));
		bec.setResource(resource);
		bundle.addEntry(bec);
		return bundle;
	}
	
	public static Age parseAge(String ageString) {
		Age returnAge = new Age();
		Pattern r = Pattern.compile(ageRegex);
	    Matcher m = r.matcher(ageString.toLowerCase());
		if(m.find()) {
			String quantity =  m.group(1);
			String type = m.group(2);
			returnAge.setValue(Double.parseDouble(quantity));
			returnAge.setSystem("http://hl7.org/fhir/ValueSet/age-units");
			switch(type) {
				case "year":
					returnAge.setUnit("a");
					returnAge.setCode("a");
					return returnAge;
				case "month":
					returnAge.setUnit("mo");
					returnAge.setCode("mo");
					return returnAge;
				case "week":
					returnAge.setUnit("wk");
					returnAge.setCode("wk");
					return returnAge;
				case "day":
					returnAge.setUnit("d");
					returnAge.setCode("d");
					return returnAge;
				case "hour":
					returnAge.setUnit("h");
					returnAge.setCode("h");
					return returnAge;
				case "minute":
					returnAge.setUnit("min");
					returnAge.setCode("min");
					return returnAge;
			}
		}

	    return null;
	}
	
	public static Address createAddress(String street, String city,
			String county, String state, String zip) {
		Address returnAddress = new Address();
		returnAddress.addLine(street);
		returnAddress.setCity(city);
		returnAddress.setDistrict(county);
		returnAddress.setState(state);
		returnAddress.setPostalCode(zip);
		return returnAddress;
	}
	
	public static HumanName parseHumanName(String name) {
		for(String nameFormat: nameFormatStrings) {
			Pattern pattern = Pattern.compile(nameFormat);
			Matcher matcher = pattern.matcher(name);
			if(matcher.find()) {
				HumanName nameResource = new HumanName();
				nameResource.setFamily(matcher.group(2));
				nameResource.addGiven(matcher.group(1));
				return nameResource;
			}
		}
		return null;
	}
}
