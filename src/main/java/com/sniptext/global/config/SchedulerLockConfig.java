package com.sniptext.global.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * 배치 잠금 (셰드락). 서버가 여러 대이거나 배포 중 새·옛 컨테이너가 겹쳐도 같은 배치가 한 번만 돌게 한다.
 * 배치 메서드에 {@code @SchedulerLock(name = "...", lockAtMostFor = "...")}을 붙여 쓴다. 이름은 배치마다 다르게 둔다.
 * <p>
 * 잠금 행은 shedlock 테이블(V202611240039__create_shedlock.sql)에 둔다.
 * 잠금 시각은 서버 시계 대신 DB 시각(UTC)으로 판단해 서버 간 시계 차이의 영향을 받지 않는다.
 * <p>
 * 스케줄 실행(SchedulingConfig)과 분리해 두었다. 테스트처럼 스케줄을 꺼도 배치 메서드를 직접 부를 때 잠금은 그대로 걸린다.
 */
@Configuration
// 잠금을 풀지 못하고 서버가 죽어도 이 시간이 지나면 다른 서버가 다시 잡을 수 있다. 배치마다 lockAtMostFor로 덮어쓴다
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

	@Bean
	public LockProvider lockProvider(DataSource dataSource) {
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
			.withJdbcTemplate(new JdbcTemplate(dataSource))
			.usingDbTime()
			.build());
	}

}
