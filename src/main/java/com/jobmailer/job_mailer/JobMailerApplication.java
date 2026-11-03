package com.jobmailer.job_mailer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobMailerApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobMailerApplication.class, args);
	}

}
