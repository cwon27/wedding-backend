package com.wedding.guestbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WeddingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeddingBackendApplication.class, args);
	}

}
