package com.sniptext.global.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각은 이 Clock에서 얻는다. 테스트에서 시각을 고정할 수 있게 하기 위해서다. */
@Configuration
public class TimeConfig {

	/** 어노테이션 값처럼 상수 문자열이 필요한 곳에서 쓴다 (예: {@code @Scheduled(zone = TimeConfig.KST_ZONE_ID)}) */
	public static final String KST_ZONE_ID = "Asia/Seoul";

	public static final ZoneId KST = ZoneId.of(KST_ZONE_ID);

	/**
	 * 마이크로초 단위로 잘린 시계. 감사 기능을 거치지 않는 edited_at·last_used_at·deleted_at도
	 * now(clock)만으로 DATETIME(6)과 같은 정밀도가 되게 하기 위해서다 (저장 전 값과 다시 읽은 값이 같아진다).
	 */
	@Bean
	public Clock clock() {
		return Clock.tick(Clock.system(KST), Duration.of(1, ChronoUnit.MICROS));
	}

}
