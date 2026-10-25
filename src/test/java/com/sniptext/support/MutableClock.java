package com.sniptext.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import com.sniptext.global.config.TimeConfig;

/** 테스트에서 현재 시각을 고정하고 앞으로 옮길 수 있는 Clock. 시간대는 한국 시간이다. */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> instant;

	public MutableClock(LocalDateTime initial) {
		this.instant = new AtomicReference<>(initial.atZone(TimeConfig.KST).toInstant());
	}

	public void setTo(LocalDateTime dateTime) {
		instant.set(dateTime.atZone(TimeConfig.KST).toInstant());
	}

	public void advance(Duration duration) {
		instant.updateAndGet(current -> current.plus(duration));
	}

	@Override
	public ZoneId getZone() {
		return TimeConfig.KST;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("테스트용 Clock은 한국 시간대만 쓴다");
	}

	@Override
	public Instant instant() {
		return instant.get();
	}

}
