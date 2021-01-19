package edu.gatech.chai.Mapping.Util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.CodeableConcept;

import edu.gatech.chai.VRDR.model.util.CommonUtil;

public class CommonMappingUtil {
	public static String trueValueRegex = "yes|true|y";
	public static String unknownValueRegex = "Unknown|Unk|\\?";
	
	public static boolean parseBoolean(String boolString) {
		Pattern r = Pattern.compile(trueValueRegex);
	    Matcher m = r.matcher(boolString.toLowerCase());
	    return m.find();
	}
	
	public static CodeableConcept parseBooleanAndCreateCode(String boolString) {
		boolean value = CommonMappingUtil.parseBoolean(boolString);
		if(value) {
			return CommonUtil.yesCode;
		}
		Pattern r = Pattern.compile(unknownValueRegex);
	    Matcher m = r.matcher(boolString.toLowerCase());
	    if(m.find()) {
	    	return CommonUtil.unknownCode;
	    }
		else {
			return CommonUtil.noCode;
		}
	}
}
