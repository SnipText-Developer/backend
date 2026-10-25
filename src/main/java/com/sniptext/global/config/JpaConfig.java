package com.sniptext.global.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 감사 기능 설정. 메인 클래스에 붙이면 웹 슬라이스 테스트가 JPA 없이 떠서 깨지므로 여기에 둔다.
 * 시각은 Clock 빈에서 얻고, DATETIME(6)에 맞춰 마이크로초로 자른다 (자르지 않으면 저장 전 값과 다시 읽은 값이 달라진다).
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

	@Bean
	public DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
	}

}
