package com.vet_saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class VetSaasApplication {

	public static void main(String[] args) {
		SpringApplication.run(VetSaasApplication.class, args);
	}

}
