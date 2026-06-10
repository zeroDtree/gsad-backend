package com.zerodtree.gsad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GsadApplication {
	public static void main(String[] args) {
		SpringApplication.run(GsadApplication.class, args);
	}
}