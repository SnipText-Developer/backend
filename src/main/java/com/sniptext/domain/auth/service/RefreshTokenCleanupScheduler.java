package com.sniptext.domain.auth.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sniptext.global.config.TimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * 매일 04:00(KST) 만료된 재발급 토큰을 지운다. 지우는 규칙은 {@link AuthCommandService#deleteExpiredRefreshTokens()}에 있다.
 * <p>
 * 서버가 여러 대이거나 배포 중 새·옛 컨테이너가 겹쳐도 셰드락으로 한 번만 돈다.
 * lockAtLeastFor는 서버끼리 시계가 조금 어긋나도, 먼저 끝난 서버가 잠금을 풀자마자 늦게 깬 서버가 다시 돌지 않게 1분 잡아 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

	static final String LOCK_NAME = "refresh-token-cleanup";

	private final AuthCommandService authCommandService;

	@Scheduled(cron = "0 0 4 * * *", zone = TimeConfig.KST_ZONE_ID)
	@SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
	public void deleteExpiredRefreshTokens() {
		int deleted = authCommandService.deleteExpiredRefreshTokens();
		log.info("만료 재발급 토큰 정리 완료: deleted={}", deleted);
	}

}
