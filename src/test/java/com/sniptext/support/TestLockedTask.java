package com.sniptext.support;

import java.util.concurrent.atomic.AtomicInteger;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * 배치 잠금(셰드락) 연결 확인용 빈. 실제 배치처럼 @SchedulerLock만 붙이고, 실행 횟수를 센다.
 * IntegrationTestSupport가 등록해 통합 테스트 컨텍스트를 새로 띄우지 않는다.
 */
public class TestLockedTask {

	public static final String LOCK_NAME = "test-locked-task";

	private final AtomicInteger runs = new AtomicInteger();

	@SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT1M")
	public void run() {
		runs.incrementAndGet();
	}

	public int runs() {
		return runs.get();
	}

	public void reset() {
		runs.set(0);
	}

}
