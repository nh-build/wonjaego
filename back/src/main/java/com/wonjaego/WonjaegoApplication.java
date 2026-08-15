package com.wonjaego;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class WonjaegoApplication {

	public static void main(String[] args) {
		SpringApplication.run(WonjaegoApplication.class, args);
	}

}
