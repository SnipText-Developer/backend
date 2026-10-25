package com.sniptext.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeConfigTest {

	@Test
	@DisplayName("운영 Clock 빈은 한국 시간대이고 마이크로초 단위로 잘려 있다")
	void clockIsTruncatedToMicros() {
		Clock clock = new TimeConfig().clock();

		assertThat(clock.getZone()).isEqualTo(TimeConfig.KST);
		// 한 번만 보면 우연히 1000의 배수일 수 있으니 여러 번 확인한다
		for (int i = 0; i < 1000; i++) {
			assertThat(LocalDateTime.now(clock).getNano() % 1000).isZero();
		}
	}

}
