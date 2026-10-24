package com.sniptext.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각은 이 Clock에서 얻는다. 테스트에서 시각을 고정할 수 있게 하기 위해서다. */
@Configuration
public class TimeConfig {

	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(KST);
	}

}
