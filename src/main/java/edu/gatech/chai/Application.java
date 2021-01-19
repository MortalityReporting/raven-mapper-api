package edu.gatech.chai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import edu.gatech.chai.VRDR.context.VRDRFhirContext;
import edu.gatech.chai.Mapping.Service.CanaryValidationService;
import edu.gatech.chai.Mapping.Service.NightingaleSubmissionService;

@SpringBootApplication
public class Application extends SpringBootServletInitializer{

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
    @Bean
    public VRDRFhirContext vrdrFhirContext() {
    	return new VRDRFhirContext();
    }
    
    @Bean
    public NightingaleSubmissionService nightingaleSubmissionService() {
    	return new NightingaleSubmissionService();
    }
    
}