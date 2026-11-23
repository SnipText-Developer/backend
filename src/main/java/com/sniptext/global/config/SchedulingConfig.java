package com.sniptext.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 배치 스케줄 실행. 배치는 {@code @Scheduled(cron = "...", zone = TimeConfig.KST_ZONE_ID)}로 한국 시간 기준으로 건다.
 * 스케줄 스레드는 스프링 기본값(1개)이라 배치는 하나씩 차례로 돈다. 배치끼리 시각이 겹치지 않게 둔다 (00:10, 04:00).
 * <p>
 * sniptext.scheduling.enabled=false면 스케줄을 켜지 않는다. 테스트에서 실제 시각이 되어 배치가 데이터를 지우지 않게 하기 위해서다
 * (build.gradle 테스트 환경 변수 SNIPTEXT_SCHEDULING_ENABLED). 운영·개발 서버는 값이 없어 켜진다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sniptext.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
