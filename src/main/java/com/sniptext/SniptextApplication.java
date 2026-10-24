package com.sniptext;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SniptextApplication {

	public static void main(String[] args) {
		// DB에 KST로 저장하므로 JVM 기본 시간대를 고정한다
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(SniptextApplication.class, args);
	}

}
