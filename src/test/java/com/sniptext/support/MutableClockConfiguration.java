package com.sniptext.support;

import java.time.LocalDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 조절 가능한 Clock을 운영 Clock 빈(clock)보다 먼저 쓰이게 등록한다.
 * 빈 덮어쓰기가 꺼져 있으므로 같은 이름을 쓰지 않고 다른 이름 + @Primary로 등록한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock(LocalDateTime.of(2026, 10, 25, 10, 0));
	}

}
