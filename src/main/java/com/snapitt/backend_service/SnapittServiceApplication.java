package com.snapitt.backend_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing // This enables @CreatedDate and @LastModifiedDate
public class SnapittServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SnapittServiceApplication.class, args);
	}

}