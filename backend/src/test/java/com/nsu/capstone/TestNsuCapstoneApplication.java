package com.nsu.capstone;

import org.springframework.boot.SpringApplication;

public class TestNsuCapstoneApplication {

	public static void main(String[] args) {
		SpringApplication.from(NsuCapstoneApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
