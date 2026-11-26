package com.sniptext.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** 스케줄 실행은 기본으로 켜지고, sniptext.scheduling.enabled=false일 때만 꺼진다 (테스트용). */
class SchedulingConfigTest {

	// 테스트 JVM에는 SNIPTEXT_SCHEDULING_ENABLED=false(build.gradle)가 있어서, "설정이 없을 때"를 보려면 시스템 환경 변수를 뺀다
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(context -> context.getEnvironment().getPropertySources()
			.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
		.withUserConfiguration(SchedulingConfig.class);

	@Test
	@DisplayName("설정이 없으면 스케줄이 켜진다 (운영·개발 서버)")
	void enabledByDefault() {
		runner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
	}

	@Test
	@DisplayName("테스트 환경 변수(SNIPTEXT_SCHEDULING_ENABLED=false)로도 꺼진다")
	void disabledByEnvironmentVariable() {
		new ApplicationContextRunner()
			.withUserConfiguration(SchedulingConfig.class)
			.run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
	}

	@Test
	@DisplayName("sniptext.scheduling.enabled=false면 스케줄이 켜지지 않는다")
	void disabledByProperty() {
		runner.withPropertyValues("sniptext.scheduling.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
	}

}
