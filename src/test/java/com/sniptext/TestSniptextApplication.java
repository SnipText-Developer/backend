package com.sniptext;

import org.springframework.boot.SpringApplication;

public class TestSniptextApplication {

	public static void main(String[] args) {
		SpringApplication.from(SniptextApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
